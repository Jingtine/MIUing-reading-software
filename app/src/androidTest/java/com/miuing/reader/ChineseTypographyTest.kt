package com.miuing.reader

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ChineseTypographyTest {
    private val text="<h1>排版测试</h1><p id='zh' style='text-indent:7px;margin-bottom:13px'>中文正文，保留<span>中英文 mixed words</span>与标点。</p>"+
        "<p id='en' style='text-indent:5px'>English words keep their original spaces.</p>"+
        "<pre><p id='code' style='text-indent:3px'>代码中文 source code</p></pre>"+
        "<blockquote><p id='quote'>引文保留自己的布局。</p></blockquote>"+
        "<p id='center' style='text-align:center'>居中标题</p><figure><figcaption><p id='caption'>图片说明</p></figcaption></figure>"
    @Test fun optionalChineseLayoutChangesOnlyBodyParagraphsAndRestoresPublisher() {
        RendererHarness(text).use {r->
            val original=r.js("document.getElementById('miuing-content').textContent")
            val styles=r.js("['zh','en','code','quote','center','caption'].map(id=>document.getElementById(id).getAttribute('style')).join('|')")
            r.configure(JSONObject().put("customLayout",true).put("chineseLayout",true).put("firstLineIndent",2).put("paragraphSpacing",.5))
            assertEquals("Chinese paragraph indent must use em units",40.0,r.js("parseFloat(getComputedStyle(document.getElementById('zh')).textIndent)").toDouble(),.01)
            assertEquals(10.0,r.js("parseFloat(getComputedStyle(document.getElementById('zh')).marginBottom)").toDouble(),.01)
            assertEquals("\"strict\"",r.js("getComputedStyle(document.getElementById('zh')).lineBreak"))
            assertEquals(5.0,r.js("parseFloat(getComputedStyle(document.getElementById('en')).textIndent)").toDouble(),.01)
            assertEquals(3.0,r.js("parseFloat(getComputedStyle(document.getElementById('code')).textIndent)").toDouble(),.01)
            assertEquals("Text and UTF-16 offsets must not be rewritten",original,r.js("document.getElementById('miuing-content').textContent"))
            assertEquals("true",r.js("['quote','center','caption'].every(id=>!document.getElementById(id).style.getPropertyValue('line-break'))"))
            r.configure(JSONObject().put("customLayout",false))
            assertEquals(styles,r.js("['zh','en','code','quote','center','caption'].map(id=>document.getElementById(id).getAttribute('style')).join('|')"))
        }
    }
    @Test fun layoutReflowKeepsTheOriginalPassageAndRecognizesSupplementaryHan() {
        val body=(0..100).joinToString("") {"<p id='p$it'>第${it}段。中文排版会变化，但应该一直保留阅读原文。A mixed English passage.</p>"}+"<p id='rare'>𰻞𠀀</p>"
        RendererHarness(body).use {r->
            r.js("window.targetOffset=new MIUingTextIndex(document.getElementById('miuing-content')).offsetOf(document.getElementById('p70').firstChild,0);window.miuingJumpAnchor(window.targetOffset)")
            r.configure(JSONObject().put("customLayout",true).put("chineseLayout",true).put("firstLineIndent",3).put("paragraphSpacing",1))
            assertEquals(60.0,r.js("parseFloat(getComputedStyle(document.getElementById('rare')).textIndent)").toDouble(),.01)
            assertEquals("true",r.js("(()=>{const r=document.getElementById('p70').getBoundingClientRect();return r.top>=20 && r.top<100})()"))
        }
    }
}
