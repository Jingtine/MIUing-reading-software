package com.miuing.reader

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class ThemeOptionsStoreTest {
    private fun preferences(block:(android.content.SharedPreferences)->Unit) {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val name="theme-options-test-${UUID.randomUUID()}"
        try {block(context.getSharedPreferences(name,0))} finally {context.deleteSharedPreferences(name)}
    }
    @Test fun legacyTypographyMigratesOnlyIntoPreviouslySelectedTheme()=preferences {p->
        p.edit().putInt("theme",2).putBoolean("serif",false).putFloat("lineHeight",1.6f).putFloat("margin",32f).putFloat("letterSpacing",.08f).commit()
        val store=ThemeOptionsStore(p)
        assertEquals("sans",store.load(2).font)
        assertEquals(1.6f,store.load(2).lineHeight,.001f)
        assertEquals(32f,store.load(2).margin,.001f)
        assertEquals(.08f,store.load(2).letterSpacing,.001f)
        assertTrue(store.load(2).customLayout)
        assertEquals("book",store.load(0).font)
        p.edit().putBoolean("serif",true).commit()
        assertEquals("Migration must not run again","sans",ThemeOptionsStore(p).load(2).font)
    }
    @Test fun savedProfilesAndResetAreIsolatedBetweenThemes()=preferences {p->
        val store=ThemeOptionsStore(p)
        store.save(0,ThemeOptions(font="mono",lineHeight=2f,margin=36f,customLayout=true))
        store.save(3,ThemeOptions(font="serif",bold=false,justify=true))
        assertEquals("mono",ThemeOptionsStore(p).load(0).font)
        assertFalse(ThemeOptionsStore(p).load(3).bold)
        store.save(3,ThemeOptions.defaults(3))
        assertEquals("sans",ThemeOptionsStore(p).load(3).font)
        assertTrue(ThemeOptionsStore(p).load(3).bold)
        assertEquals(36f,ThemeOptionsStore(p).load(0).margin,.001f)
        assertEquals("mono",ThemeOptionsStore(p).load(0).font)
    }
    @Test fun invalidStoredOptionsFallBackToUsableValues()=preferences {p->
        val store=ThemeOptionsStore(p)
        store.save(1,ThemeOptions(font="unavailable",lineHeight=Float.NaN,margin=999f,letterSpacing=-2f,wordSpacing=-1f))
        val restored=ThemeOptionsStore(p).load(1)
        assertEquals("serif",restored.font)
        assertEquals(1.85f,restored.lineHeight,.001f)
        assertEquals(48f,restored.margin,.001f)
        assertEquals(0f,restored.letterSpacing,.001f)
        assertEquals(0f,restored.wordSpacing,.001f)
    }
    @Test fun bundledBodyFontsSurviveSavingAndThemeReload()=preferences {p->
        for(id in listOf("buming","source-han-serif","fz-jingsong","kinghwa")) {
            ThemeOptionsStore(p).save(0,ThemeOptions(font=id))
            assertEquals("Bundled body font was discarded during normalization",id,ThemeOptionsStore(p).load(0).font)
            assertEquals("Changing the original theme leaked into another theme","serif",ThemeOptionsStore(p).load(2).font)
        }
    }
}
