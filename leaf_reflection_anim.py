import numpy as np
from PIL import Image, ImageDraw
import imageio
import random
import math
import os

# Configuration constants
W, H = 1440, 1600
FPS = 30
LOOP_SECONDS = 3.0
WAVE_LIFE = 6.0
SIGMA = 80.0
MAX_ALPHA = 0.80
FADE_PX = 24
TILE_W = 48
MIN_SPACING = 2*TILE_W
DENSITY_SCALE = 0.50
RNG_SEED = 1337
LEAF_ONLY_PATH = "CloudCounter/app/src/main/res/drawable/leaf_only.png"
LEAF_FALLBACK = "leaf.png"
CROP_BOTTOM_FRAC = 0.18
GREEN_DOM_DELTA = 10
LEAF_MIN_G = 80
WHITE_MIN = 200
SNAPSHOT_TIME = 1.50

def load_or_extract_leaf():
    """Load leaf_only.png or extract from leaf.png"""
    if os.path.exists(LEAF_ONLY_PATH):
        print(f"✅ Using existing {LEAF_ONLY_PATH}")
        return Image.open(LEAF_ONLY_PATH).convert("RGBA")
    
    if not os.path.exists(LEAF_FALLBACK):
        print(f"❌ Neither {LEAF_ONLY_PATH} nor {LEAF_FALLBACK} found")
        # Create a simple leaf shape as fallback
        leaf = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
        draw = ImageDraw.Draw(leaf)
        draw.ellipse([10, 10, 54, 54], fill=(34, 139, 34, 255))
        print("🍃 Created simple leaf fallback")
        return leaf
    
    print(f"🔄 Extracting leaf from {LEAF_FALLBACK}")
    img = Image.open(LEAF_FALLBACK).convert("RGBA")
    
    # Crop bottom portion
    crop_height = int(img.height * (1 - CROP_BOTTOM_FRAC))
    img = img.crop((0, 0, img.width, crop_height))
    
    # Extract green leaf pixels
    pixels = np.array(img)
    r, g, b, a = pixels[:,:,0], pixels[:,:,1], pixels[:,:,2], pixels[:,:,3]
    
    # Green dominant condition
    green_mask = (g > r + GREEN_DOM_DELTA) & (g > b + GREEN_DOM_DELTA) & (g >= LEAF_MIN_G)
    # Exclude white areas
    white_mask = (np.minimum(np.minimum(r, g), b) >= WHITE_MIN)
    
    final_mask = green_mask & ~white_mask
    pixels[:,:,3] = np.where(final_mask, a, 0)
    
    leaf = Image.fromarray(pixels, "RGBA")
    leaf.save(LEAF_ONLY_PATH)
    print(f"💾 Saved extracted leaf as {LEAF_ONLY_PATH}")
    return leaf

def generate_leaf_positions():
    """Generate fixed leaf positions using dart-throw sampling"""
    random.seed(RNG_SEED)
    positions = []
    max_attempts = 10000
    
    # Estimate target count
    area = W * H
    tile_area = TILE_W * TILE_W
    target_count = int((area / tile_area) * DENSITY_SCALE)
    
    attempts = 0
    while len(positions) < target_count and attempts < max_attempts:
        x = random.randint(0, W - TILE_W)
        y = random.randint(0, H - TILE_W)
        
        # Check minimum distance
        too_close = False
        for px, py in positions:
            if math.sqrt((x - px)**2 + (y - py)**2) < MIN_SPACING:
                too_close = True
                break
        
        if not too_close:
            positions.append((x, y))
        
        attempts += 1
    
    print(f"🍃 Generated {len(positions)} leaf positions")
    return positions

def create_fade_mask():
    """Create vertical fade mask for top/bottom edges"""
    mask = np.ones((H, W), dtype=np.float32)
    
    # Top fade
    for y in range(FADE_PX):
        alpha = y / FADE_PX
        mask[y, :] = alpha
    
    # Bottom fade
    for y in range(H - FADE_PX, H):
        alpha = (H - 1 - y) / FADE_PX
        mask[y, :] = alpha
    
    return mask

def wave_intensity(t, t0, center_x, center_y):
    """Calculate wave intensity at time t for wave starting at t0"""
    if t < t0:
        return np.zeros((H, W), dtype=np.float32)
    
    elapsed = t - t0
    radius = (math.sqrt(center_x**2 + center_y**2) + 100) * elapsed / WAVE_LIFE
    
    # Create coordinate grids
    y, x = np.mgrid[0:H, 0:W]
    dist = np.sqrt((x - center_x)**2 + (y - center_y)**2)
    
    # Gaussian wave profile
    intensity = np.exp(-0.5 * ((dist - radius) / SIGMA)**2)
    return intensity

def render_frame(t, leaf_img, positions, fade_mask):
    """Render a single frame at time t"""
    # Create transparent canvas
    frame = np.zeros((H, W, 4), dtype=np.uint8)
    
    # Calculate combined wave intensity (two overlapping waves)
    center_x, center_y = W // 2, H // 2
    wave1 = wave_intensity(t, 0.0, center_x, center_y)
    wave2 = wave_intensity(t, -3.0, center_x, center_y)
    combined_wave = np.clip(wave1 + wave2, 0, 1) * MAX_ALPHA
    
    # Apply fade mask
    combined_wave *= fade_mask
    
    # Place leaves
    leaf_array = np.array(leaf_img)
    leaf_h, leaf_w = leaf_array.shape[:2]
    
    for x, y in positions:
        # Skip if leaf would be outside canvas
        if x + leaf_w > W or y + leaf_h > H:
            continue
        
        # Get wave intensity for this leaf position
        leaf_intensity = combined_wave[y:y+leaf_h, x:x+leaf_w]
        
        # Apply leaf with wave-controlled alpha
        leaf_region = leaf_array.copy()
        if leaf_region.shape[2] == 4:  # Has alpha channel
            original_alpha = leaf_region[:,:,3].astype(np.float32) / 255.0
            wave_alpha = np.mean(leaf_intensity) if leaf_intensity.size > 0 else 0
            final_alpha = (original_alpha * wave_alpha * 255).astype(np.uint8)
            leaf_region[:,:,3] = final_alpha
            
            # Composite onto frame
            frame[y:y+leaf_h, x:x+leaf_w] = leaf_region
    
    return Image.fromarray(frame, "RGBA")

def main():
    print("🍃 Starting leaf reflection animation generation...")
    
    # Load leaf image
    leaf_img = load_or_extract_leaf()
    leaf_img = leaf_img.resize((TILE_W, TILE_W), Image.Resampling.LANCZOS)
    
    # Generate leaf positions
    positions = generate_leaf_positions()
    
    # Create fade mask
    fade_mask = create_fade_mask()
    
    # Generate scattered background (static)
    print("🎨 Creating scattered background...")
    scattered_frame = render_frame(0.0, leaf_img, positions, np.ones((H, W)))
    scattered_frame.save("CloudCounter/app/src/main/res/drawable/leaf_scattered_bg_1440x1600_new.png")
    print("💾 Saved leaf_scattered_bg_1440x1600_new.png")
    
    # Generate snapshot (mid-wave)
    print("📸 Creating snapshot...")
    snapshot_frame = render_frame(SNAPSHOT_TIME, leaf_img, positions, fade_mask)
    snapshot_frame.save("CloudCounter/app/src/main/res/drawable/leaf_reflection_halfway_new.png")
    print("💾 Saved leaf_reflection_halfway_new.png")
    
    # Generate animated WebP
    print("🎬 Creating animated WebP...")
    frames = []
    total_frames = int(FPS * LOOP_SECONDS)
    
    for i in range(total_frames):
        t = i / FPS
        frame = render_frame(t, leaf_img, positions, fade_mask)
        frames.append(frame)
        if i % 10 == 0:
            print(f"  Frame {i+1}/{total_frames}")
    
    # Save as GIF first (fallback for WebP issues)
    gif_path = "CloudCounter/app/src/main/res/drawable/leaf_reflection_1440x1600.gif"
    frames[0].save(
        gif_path,
        save_all=True,
        append_images=frames[1:],
        duration=1000//FPS,
        loop=0,
        optimize=True
    )
    print(f"💾 Saved {gif_path}")
    
    # Try to convert to WebP using external tool if available
    webp_path = "CloudCounter/app/src/main/res/drawable/leaf_reflection_1440x1600.webp"
    try:
        import subprocess
        result = subprocess.run(['gif2webp', '-lossy', '-q', '85', gif_path, '-o', webp_path], 
                              capture_output=True, text=True)
        if result.returncode == 0:
            print(f"💾 Converted to {webp_path}")
            os.remove(gif_path)  # Remove GIF if WebP conversion successful
        else:
            print("⚠️  WebP conversion failed, keeping GIF format")
    except (subprocess.SubprocessError, FileNotFoundError):
        print("⚠️  gif2webp not available, keeping GIF format")
    print(f"✅ Generated {total_frames} frames at {FPS} FPS")

if __name__ == "__main__":
    main()