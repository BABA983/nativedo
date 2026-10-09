package io.github.baba983.nativedo.core.web

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The app's topic-link rule: the site's own `/t/...` URL, the two custom-scheme aliases, and the
 * app-owned `/t/...` shape carried by those aliases - and everything that must be refused.
 *
 * <p>The custom schemes are fixed app-owned routing, not origins: they may not name a foreign host,
 * carry userinfo or a port, or smuggle a different scheme (notably Telegram's `tg`/`tgb`) into this
 * client.</p>
 */
@RunWith(RobolectricTestRunner::class)
internal class DiscourseLinksTest {

    private val base = "https://linux.do/"

    @Test
    fun theCustomSchemeAliasesReadTheIdAndTheOptionalFloor() {
        assertEquals(DiscourseTopicLink(2193859L, 0), DiscourseLinks.topicLink("nativedo://topic/2193859", base))
        assertEquals(DiscourseTopicLink(2193859L, 4), DiscourseLinks.topicLink("nativedo://topic/2193859/4", base))
        assertEquals(DiscourseTopicLink(123L, 0), DiscourseLinks.topicLink("linuxdo://topic/123", base))
        assertEquals(DiscourseTopicLink(123L, 7), DiscourseLinks.topicLink("linuxdo://topic/123/7", base))
    }

    @Test
    fun theCustomSchemeCarriesTheAppOwnedTopicPathForTheForumHost() {
        assertEquals(
            DiscourseTopicLink(101L, 0),
            DiscourseLinks.topicLink("nativedo://linux.do/t/welcome-to-linux-do/101", base),
        )
        assertEquals(DiscourseTopicLink(101L, 3), DiscourseLinks.topicLink("linuxdo://linux.do/t/101/3", base))
        assertEquals(
            DiscourseTopicLink(101L, 3),
            DiscourseLinks.topicLink("nativedo://linux.do/t/welcome-to-linux-do/101/3", base),
        )
    }

    @Test
    fun theCustomSchemeRefusesForeignHostsUserinfoPortsAndOtherSchemes() {
        // A foreign host is not the configured forum, whether it is used as the `topic` token or as
        // the `/t/` authority.
        assertNull(DiscourseLinks.topicLink("nativedo://evil.example.com/t/101", base))
        assertNull(DiscourseLinks.topicLink("linuxdo://evil.example.com/topic/101", base))
        // Userinfo and an explicit port have no meaning on the app's fixed routes.
        assertNull(DiscourseLinks.topicLink("nativedo://reader@topic/101", base))
        assertNull(DiscourseLinks.topicLink("nativedo://topic:8443/101", base))
        // Telegram's own schemes are not this app's to open.
        assertNull(DiscourseLinks.topicLink("tg://resolve?domain=telegram", base))
        assertNull(DiscourseLinks.topicLink("tgb://open", base))
        assertNull(DiscourseLinks.topicLink("tonsite://open", base))
    }

    @Test
    fun theCustomSchemeRefusesBadIdsAndFloors() {
        assertNull(DiscourseLinks.topicLink("nativedo://topic/0", base))
        assertNull(DiscourseLinks.topicLink("nativedo://topic/-1", base))
        assertNull(DiscourseLinks.topicLink("nativedo://topic/abc", base))
        assertNull(DiscourseLinks.topicLink("nativedo://topic/99999999999999999999", base))
        assertNull(DiscourseLinks.topicLink("nativedo://topic/101/0", base))
        assertNull(DiscourseLinks.topicLink("nativedo://topic/101/-2", base))
        assertNull(DiscourseLinks.topicLink("nativedo://topic/101/99999999999999999999", base))
        assertNull(DiscourseLinks.topicLink("nativedo://topic/101/2/3", base))
        assertNull(DiscourseLinks.topicLink("nativedo://topic/", base))
    }

    @Test
    fun theHttpRuleIsUnchangedAndStillRefusesAForeignOrigin() {
        assertEquals(DiscourseTopicLink(101L, 3), DiscourseLinks.topicLink("https://linux.do/t/101/3", base))
        assertEquals(DiscourseTopicLink(101L, 3), DiscourseLinks.topicLink("https://linux.do/t/101/3", "https://linux.do"))
        assertNull(DiscourseLinks.topicLink("https://evil.example.com/t/101/3", base))
        assertNull(DiscourseLinks.topicLink("http://linux.do/t/101", base))
    }
}
