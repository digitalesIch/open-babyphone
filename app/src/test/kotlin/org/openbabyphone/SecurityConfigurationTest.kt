/*
 * This file is part of Open Babyphone.
 *
 * Open Babyphone is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Open Babyphone is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Open Babyphone. If not, see <http://www.gnu.org/licenses/>.
 */
package org.openbabyphone

import android.app.Application
import android.content.Intent
import android.net.Uri
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.openbabyphone.navigation.Listen
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.xmlpull.v1.XmlPullParser
import java.io.File

@RunWith(RobolectricTestRunner::class)
class SecurityConfigurationTest {
    @Test
    fun `listen route has no credential field`() {
        val fieldNames = Listen::class.java.declaredFields.map { it.name }

        assertFalse(fieldNames.any { it.contains("code", ignoreCase = true) })
        assertTrue(fieldNames.contains("requestId"))
    }

    @Test
    fun `legacy backup excludes credential preference domains`() {
        assertEquals(setOf("root", "sharedpref", "device_sharedpref"), excludedDomains(R.xml.backup_rules).toSet())
    }

    @Test
    fun `cloud backup and device transfer exclude credential preference domains`() {
        val domains = excludedDomains(R.xml.data_extraction_rules)

        assertEquals(2, domains.count { it == "sharedpref" })
        assertEquals(2, domains.count { it == "device_sharedpref" })
    }

    @Test
    fun `manifest references legacy backup rules`() {
        val manifest = File("src/main/AndroidManifest.xml").readText()

        assertTrue(manifest.contains("android:fullBackupContent=\"@xml/backup_rules\""))
        assertTrue(manifest.contains("android:dataExtractionRules=\"@xml/data_extraction_rules\""))
        assertFalse(manifest.contains("quiet-engine"))
        assertTrue(manifest.contains("android:name=\".ListenResumeActivity\""))
        assertTrue(manifest.contains("android:exported=\"false\""))
    }

    @Test
    fun `listen uri cannot resolve to an exported app activity`() {
        val context = RuntimeEnvironment.getApplication() as Application
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("quiet-engine://listen"))
            .setPackage(context.packageName)

        assertEquals(null, intent.resolveActivity(context.packageManager))
    }

    private fun excludedDomains(resourceId: Int): List<String> {
        val context = RuntimeEnvironment.getApplication() as Application
        val parser = context.resources.getXml(resourceId)
        val domains = mutableListOf<String>()
        while (parser.eventType != XmlPullParser.END_DOCUMENT) {
            if (parser.eventType == XmlPullParser.START_TAG && parser.name == "exclude") {
                parser.getAttributeValue(null, "domain")?.let(domains::add)
            }
            parser.next()
        }
        parser.close()
        return domains
    }
}
