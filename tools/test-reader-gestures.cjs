// Exercise input ordering before asynchronous native callbacks can run.
const {test}=require('node:test');
const assert=require('node:assert/strict');
const {readFileSync}=require('node:fs');
const {resolve}=require('node:path');
const {runInNewContext}=require('node:vm');

function reader(chapterDelta=0,pageTurn='curl') {
  const handlers={},calls=[];let now=0;
  const native={};
  for(const name of ['dragBegin','dragMove','dragEnd','dragAbort','chapter','tap']) native[name]=(...args)=>calls.push([name,...args]);
  const window={};
  runInNewContext(readFileSync(resolve(__dirname,'../app/src/main/assets/reader-gestures.js'),'utf8'),{
    window,document:{addEventListener:(name,fn)=>handlers[name]=fn},MIUing:native,
    getSelection:()=>({toString:()=>''}),Date:{now:()=>now}
  });
  new window.MIUingReaderGestures({
    config:()=>({width:360,pageTurn,dragEnabled:true}),revision:()=>0,
    target:()=>chapterDelta===null?null:({chapterDelta,position:0}),advance:direction=>calls.push(['advance',direction]),
    turn:direction=>calls.push(['turn',direction])
  });
  return {
    calls,
    event(name,x,fingers=1) {
      now+=20;
      handlers[name]({target:{closest:()=>null},clientX:x,detail:0,touches:Array.from({length:fingers},()=>({})),changedTouches:[{clientX:x,clientY:200}],preventDefault(){}});
    }
  };
}
test('keyboard cancellation consumes the held touch before the native turn runs',()=>{
  const r=reader();r.event('touchstart',300);r.event('touchmove',120);
  r.event('click',340);r.event('touchend',120);
  assert.deepEqual(r.calls.filter(c=>c[0]==='turn'),[['turn',1]]);
});
test('a click completing a released chapter drag cannot navigate the old document again',()=>{
  const r=reader(1);r.event('touchstart',300);r.event('touchmove',120);r.event('touchend',120);
  r.event('click',10);
  assert.deepEqual(r.calls.filter(c=>['turn','chapter'].includes(c[0])),[['chapter',1]]);
});
test('a second finger cancels the held preview and consumes its release',()=>{
  const r=reader();r.event('touchstart',300);r.event('touchmove',120);r.event('touchstart',200,2);r.event('touchend',20);
  assert.equal(r.calls.filter(c=>c[0]==='dragAbort').length,1);
  assert.equal(r.calls.filter(c=>['turn','chapter','advance'].includes(c[0])).length,0);
});
test('dragging beyond a book edge does not prepare or navigate a page',()=>{
  const r=reader(null);r.event('touchstart',300);r.event('touchmove',120);r.event('touchend',120);
  assert.equal(r.calls.length,0);
});
test('slide prepares a held preview and requests one native commit on release',()=>{
  const r=reader(0,'slide');r.event('touchstart',300);r.event('touchmove',120);
  assert.equal(r.calls.filter(c=>c[0]==='dragBegin').length,1);
  assert.equal(r.calls.filter(c=>c[0]==='advance').length,0);
  r.event('touchend',120);
  assert.deepEqual(r.calls.filter(c=>c[0]==='dragEnd').map(c=>c.slice(2)),[[true]]);
});
