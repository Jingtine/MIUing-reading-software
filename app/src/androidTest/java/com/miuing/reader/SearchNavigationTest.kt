package com.miuing.reader

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SearchNavigationTest {
    @Test fun repeatedMatchesMapAcrossInlineElementsAndCaseExpansions() {
        val paragraph="<p>İ 😀 prefix [<em>A+B</em>] alpha&nbsp;beta Kelvin tail.</p>"
        RendererHarness(paragraph.repeat(3)).use {r->
            assertEquals("true",r.js("""(()=>{
                const root=document.getElementById('miuing-content'),index=new MIUingTextIndex(root);
                const text=root.textContent,first=text.indexOf('['),second=text.indexOf('[',first+1),last=text.lastIndexOf('[');
                const hit=index.find('[A+B] alpha beta kelvin',1),end=index.find('[A+B] alpha beta kelvin',999);
                return hit.start===second && hit.index===1 && hit.total===3 && end.start===last && end.index===2 && end.total===3;
            })()"""))
        }
    }
    @Test fun selectedMatchAndOverlappingNoteKeepTheExactText() {
        val html="<p>needle one.</p><p>needle two.</p><p>needle three.</p>"
        val text="needle one.needle two.needle three."
        val start=text.lastIndexOf("needle")
        val patch=JSONObject().put("search","needle").put("searchIndex",2)
            .put("notes",org.json.JSONArray().put(JSONObject().put("start",start+1).put("end",start+4).put("text","eed")))
        RendererHarness(html,patch).use {r->
            assertEquals("true",r.js("""(()=>{
                const root=document.getElementById('miuing-content'),index=new MIUingTextIndex(root);
                const marks=Array.from(root.querySelectorAll('[data-miuing-search]'));
                return root.textContent===${JSONObject.quote(text)} && index.offsetOf(marks[0].firstChild,0)===$start &&
                    marks.map(m=>m.textContent).join('')==='needle' && !!root.querySelector('[data-miuing][data-miuing-search]');
            })()"""))
        }
    }
    @Test fun largeMatchCountHasExactLastRangeAndEmptyQueriesStayEmpty() {
        RendererHarness("<p>"+"x ".repeat(10005)+"</p>").use {r->
            assertEquals("true",r.js("""(()=>{
                const index=new MIUingTextIndex(document.getElementById('miuing-content'));
                const hit=index.find('x',10004);
                return hit.total===10005 && hit.index===10004 && hit.start===20008 && hit.end===20009 &&
                    index.find('   ',0)===null && index.find('missing',0)===null;
            })()"""))
        }
    }
}
