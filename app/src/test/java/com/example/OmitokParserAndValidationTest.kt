package com.example

import com.example.data.model.OmitokJsonParser
import com.example.i18n.AppLanguage
import com.example.i18n.StringsProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class OmitokParserAndValidationTest {

    @Test
    fun parseVideoList_handlesWrappedAndArrayFormats() {
        val wrapped = """
            {
              "videos": [
                {
                  "id": 42,
                  "owner_id": 7,
                  "username": "omid_dev",
                  "title": "Demo Clip",
                  "description": "Vertical stream test",
                  "views": 120,
                  "status": "published",
                  "published_at": "2026-11-13"
                }
              ]
            }
        """.trimIndent()

        val parsed = OmitokJsonParser.parseVideoList(
            wrapped,
            "https://omitok07090.pythonanywhere.com"
        )
        assertEquals(1, parsed.size)
        val video = parsed.first()
        assertEquals("42", video.id)
        assertEquals("omid_dev", video.authorUsername)
        assertEquals(
            "https://omitok07090.pythonanywhere.com/videos/42/stream",
            video.resolvedStreamUrl("https://omitok07090.pythonanywhere.com")
        )
    }

    @Test
    fun parseAuthSession_extractsTokenAndUser() {
        val raw = """
            {
              "token": "abc.def.ghi",
              "user": {
                "id": 9,
                "username": "sara_99",
                "display_name": "Sara",
                "bio": "Creator"
              }
            }
        """.trimIndent()
        val session = OmitokJsonParser.parseAuthSession(raw, "fallback")
        assertEquals("abc.def.ghi", session.token)
        assertEquals("sara_99", session.user.username)
        assertEquals("Sara", session.user.displayName)
    }

    @Test
    fun bilingualStrings_provideBothPersianAndEnglish() {
        val fa = StringsProvider.get(AppLanguage.FA)
        val en = StringsProvider.get(AppLanguage.EN)
        assertTrue(fa.signIn.isNotBlank() && fa.signIn != en.signIn)
        assertEquals("OMITOK", fa.appName)
        assertEquals("OMITOK", en.appName)
    }
}
