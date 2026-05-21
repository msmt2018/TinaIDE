package com.wuxianggujun.tinaide.ui.projectlist

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class AnnouncementRewardModelsTest {

    @Test
    fun reward_shouldDefaultToUnclaimedAndNotClaimable() {
        val reward = AnnouncementReward(quotaAmount = 2048L)

        assertThat(reward.quotaAmount).isEqualTo(2048L)
        assertThat(reward.quotaExpiresAtMillis).isNull()
        assertThat(reward.claimed).isFalse()
        assertThat(reward.canClaim).isFalse()
        assertThat(reward.claimedAtMillis).isNull()
    }

    @Test
    fun announcement_shouldPreserveReadMetadataAndReward() {
        val reward = AnnouncementReward(
            quotaAmount = 4096L,
            quotaExpiresAtMillis = 200L,
            claimed = true,
            canClaim = false,
            claimedAtMillis = 150L,
        )

        val announcement = Announcement(
            id = "release-1",
            type = AnnouncementType.NEW_RELEASE,
            title = "New release",
            bodyContent = "body",
            content = "markdown",
            actionText = "Open",
            actionUrl = "https://example.com",
            isPopup = true,
            dismissible = false,
            timestamp = 100L,
            expiresAtMillis = 300L,
            receivedAtMillis = 110L,
            isRead = true,
            readAtMillis = 120L,
            reward = reward,
        )

        assertThat(announcement.isRead).isTrue()
        assertThat(announcement.readAtMillis).isEqualTo(120L)
        assertThat(announcement.reward).isEqualTo(reward)
        assertThat(announcement.dismissible).isFalse()
        assertThat(announcement.actionUrl).isEqualTo("https://example.com")
    }
}
