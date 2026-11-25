package com.miuing.reader

import org.junit.Assert.*
import org.junit.Test

class BookSearchTest {
    @Test fun allHitsUseLiveDomRulesAndBoundedFoliateStyleExcerpts() {
        RendererHarness("<style>.hidden{display:none}</style><p>İ 😀 lead <em>needle</em> one.</p><p class='hidden'>needle hidden</p><p>needle two.</p><p>needle three.</p>").use {r->
            assertEquals("true",r.js("(()=>{const i=new MIUingTextIndex(document.getElementById('miuing-content')),a=i.findAll('NEEDLE',2);return a.hits.length===2 && a.more && a.hits[0].index===0 && a.hits[1].index===1 && a.hits[0].match==='needle' && a.hits[1].post.includes('two') && !a.hits[1].post.includes('hidden')})()"))
            assertEquals("true",r.js("(()=>{const a=new MIUingTextIndex(document.getElementById('miuing-content')).findAll('   ',10);return a.hits.length===0 && !a.more})()"))
        }
    }
    @Test fun nestedHiddenBlocksDoNotBecomeSearchResults() {
        RendererHarness("<div style='display:none'><p>needle hidden</p></div><p>needle visible</p>").use {r->
            assertEquals("true",r.js("(()=>{const i=new MIUingTextIndex(document.getElementById('miuing-content'));return i.find('needle',0).total===1 && i.findAll('needle',10).hits.length===1})()"))
        }
    }

    @Test fun visibleOrdinalsCanChangeWithoutChangingTheSamePassageAnchor() {
        RendererHarness("<p id='first'>needle one</p><p>needle two</p>").use { r ->
            assertEquals("true", r.js("""(()=>{
                const index=new MIUingTextIndex(document.getElementById('miuing-content'));
                const before=index.findAll('needle').hits;
                document.getElementById('first').style.display='none';
                const after=index.findAll('needle').hits;
                return before.length===2 && after.length===1 && after[0].index===0 &&
                    after[0].start===before[1].start && after[0].start===index.find('needle',0).start;
            })()"""))
        }
    }
    @Test fun largeExpandedUnicodeTextHasBoundedExcerptsAndExplicitLimit() {
        RendererHarness("<p>İ 😀 "+"needle ".repeat(1500)+"</p>").use {r->
            assertEquals("true",r.js("(()=>{const a=new MIUingTextIndex(document.getElementById('miuing-content')).findAll('needle',1000);return a.more && a.hits.length===1000 && a.hits[999].index===999 && a.hits.every(h=>Array.from(h.pre).length<=50 && Array.from(h.post).length<=50)})()"))
        }
    }
}
