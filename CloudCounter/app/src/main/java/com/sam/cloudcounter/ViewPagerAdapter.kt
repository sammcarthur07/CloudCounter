package com.sam.cloudcounter

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter

/**
 * Now supports 8 tabs: History, Sesh, Stats, Graph, Stash, Chat, Goals, and About/Inbox.
 */
class ViewPagerAdapter(
    fragmentActivity: FragmentActivity,
    private val fragments: List<Fragment>
) : FragmentStateAdapter(fragmentActivity) {

    override fun getItemCount(): Int = fragments.size

    override fun createFragment(position: Int): Fragment =
        fragments.getOrElse(position) { throw IllegalArgumentException("Invalid position $position") }

    override fun containsItem(itemId: Long): Boolean {
        return itemId >= 0 && itemId < fragments.size
    }

    override fun getItemId(position: Int): Long {
        return position.toLong()
    }
}