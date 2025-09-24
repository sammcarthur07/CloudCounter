// Cloud Functions update for cigarette statistics
// This code should be added to your existing Cloud Functions that calculate room statistics

// When calculating per-smoker statistics in the room document
// Add this to your existing stats calculation function:

function calculateRoomStatistics(activities) {
  const perSmokerStats = {};
  const overallStats = {
    totalCones: 0,
    totalJoints: 0,
    totalBowls: 0,
    totalCigarettes: 0, // ADD THIS
    sinceLastConeMs: 0,
    sinceLastJointMs: 0,
    sinceLastBowlMs: 0,
    sinceLastCigaretteMs: 0, // ADD THIS
  };

  // Group activities by smoker
  const activitiesBySmoker = {};
  activities.forEach(activity => {
    if (!activitiesBySmoker[activity.smokerId]) {
      activitiesBySmoker[activity.smokerId] = [];
    }
    activitiesBySmoker[activity.smokerId].push(activity);
  });

  // Calculate stats for each smoker
  Object.keys(activitiesBySmoker).forEach(smokerId => {
    const smokerActivities = activitiesBySmoker[smokerId];
    const smokerName = smokerActivities[0]?.smokerName || 'Unknown';
    
    // Filter activities by type
    const cones = smokerActivities.filter(a => a.type === 'CONE');
    const joints = smokerActivities.filter(a => a.type === 'JOINT');
    const bowls = smokerActivities.filter(a => a.type === 'BOWL');
    const cigarettes = smokerActivities.filter(a => a.type === 'CIGARETTE'); // ADD THIS
    
    // Calculate gap statistics for cigarettes (ADD THIS SECTION)
    const cigaretteGaps = calculateGapStats(cigarettes);
    
    perSmokerStats[smokerId] = {
      smokerName: smokerName,
      totalCones: cones.length,
      totalJoints: joints.length,
      totalBowls: bowls.length,
      totalCigarettes: cigarettes.length, // ADD THIS
      avgGapMs: calculateAverageGap(smokerActivities),
      longestGapMs: calculateLongestGap(smokerActivities),
      shortestGapMs: calculateShortestGap(smokerActivities),
      avgJointGapMs: calculateAverageGap(joints),
      longestJointGapMs: calculateLongestGap(joints),
      shortestJointGapMs: calculateShortestGap(joints),
      avgBowlGapMs: calculateAverageGap(bowls),
      longestBowlGapMs: calculateLongestGap(bowls),
      shortestBowlGapMs: calculateShortestGap(bowls),
      avgCigaretteGapMs: cigaretteGaps.avg, // ADD THIS
      longestCigaretteGapMs: cigaretteGaps.longest, // ADD THIS
      shortestCigaretteGapMs: cigaretteGaps.shortest, // ADD THIS
      lastActivityTime: Math.max(...smokerActivities.map(a => a.timestamp))
    };
    
    // Update overall stats
    overallStats.totalCones += cones.length;
    overallStats.totalJoints += joints.length;
    overallStats.totalBowls += bowls.length;
    overallStats.totalCigarettes += cigarettes.length; // ADD THIS
  });
  
  // Calculate time since last activity of each type
  const now = Date.now();
  const lastCone = activities.filter(a => a.type === 'CONE').sort((a, b) => b.timestamp - a.timestamp)[0];
  const lastJoint = activities.filter(a => a.type === 'JOINT').sort((a, b) => b.timestamp - a.timestamp)[0];
  const lastBowl = activities.filter(a => a.type === 'BOWL').sort((a, b) => b.timestamp - a.timestamp)[0];
  const lastCigarette = activities.filter(a => a.type === 'CIGARETTE').sort((a, b) => b.timestamp - a.timestamp)[0]; // ADD THIS
  
  overallStats.sinceLastConeMs = lastCone ? now - lastCone.timestamp : 0;
  overallStats.sinceLastJointMs = lastJoint ? now - lastJoint.timestamp : 0;
  overallStats.sinceLastBowlMs = lastBowl ? now - lastBowl.timestamp : 0;
  overallStats.sinceLastCigaretteMs = lastCigarette ? now - lastCigarette.timestamp : 0; // ADD THIS
  
  return {
    perSmokerStats: perSmokerStats,
    ...overallStats
  };
}

// Helper function to calculate gap statistics
function calculateGapStats(activities) {
  if (activities.length < 2) {
    return { avg: 0, longest: 0, shortest: 0 };
  }
  
  const sorted = [...activities].sort((a, b) => a.timestamp - b.timestamp);
  const gaps = [];
  
  for (let i = 1; i < sorted.length; i++) {
    gaps.push(sorted[i].timestamp - sorted[i - 1].timestamp);
  }
  
  if (gaps.length === 0) {
    return { avg: 0, longest: 0, shortest: 0 };
  }
  
  return {
    avg: gaps.reduce((a, b) => a + b, 0) / gaps.length,
    longest: Math.max(...gaps),
    shortest: Math.min(...gaps)
  };
}

function calculateAverageGap(activities) {
  if (activities.length < 2) return 0;
  const sorted = [...activities].sort((a, b) => a.timestamp - b.timestamp);
  let totalGap = 0;
  let gapCount = 0;
  
  for (let i = 1; i < sorted.length; i++) {
    totalGap += sorted[i].timestamp - sorted[i - 1].timestamp;
    gapCount++;
  }
  
  return gapCount > 0 ? Math.round(totalGap / gapCount) : 0;
}

function calculateLongestGap(activities) {
  if (activities.length < 2) return 0;
  const sorted = [...activities].sort((a, b) => a.timestamp - b.timestamp);
  let longest = 0;
  
  for (let i = 1; i < sorted.length; i++) {
    const gap = sorted[i].timestamp - sorted[i - 1].timestamp;
    if (gap > longest) longest = gap;
  }
  
  return longest;
}

function calculateShortestGap(activities) {
  if (activities.length < 2) return 0;
  const sorted = [...activities].sort((a, b) => a.timestamp - b.timestamp);
  let shortest = Infinity;
  
  for (let i = 1; i < sorted.length; i++) {
    const gap = sorted[i].timestamp - sorted[i - 1].timestamp;
    if (gap < shortest) shortest = gap;
  }
  
  return shortest === Infinity ? 0 : shortest;
}

// IMPORTANT: Update your Firestore trigger that updates room stats
// to call calculateRoomStatistics and include the new cigarette fields:

exports.updateRoomStats = functions.firestore
  .document('rooms/{roomId}')
  .onWrite(async (change, context) => {
    const roomData = change.after.exists ? change.after.data() : null;
    if (!roomData) return;
    
    const activities = roomData.activities || [];
    const stats = calculateRoomStatistics(activities);
    
    // Update the room with new stats including cigarette data
    return change.after.ref.update({
      currentStats: {
        totalCones: stats.totalCones,
        totalJoints: stats.totalJoints,
        totalBowls: stats.totalBowls,
        totalCigarettes: stats.totalCigarettes, // NOW INCLUDES CIGARETTES
        longestGapMs: stats.longestGapMs || 0,
        shortestGapMs: stats.shortestGapMs || 0,
        sinceLastConeMs: stats.sinceLastConeMs,
        sinceLastJointMs: stats.sinceLastJointMs,
        sinceLastBowlMs: stats.sinceLastBowlMs,
        sinceLastCigaretteMs: stats.sinceLastCigaretteMs, // NOW INCLUDES CIGARETTES
        perSmokerStats: stats.perSmokerStats, // NOW INCLUDES CIGARETTE STATS PER SMOKER
        lastCalculated: Date.now()
      },
      updatedAt: Date.now()
    });
  });