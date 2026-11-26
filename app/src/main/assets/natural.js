/* Offline presentation. The native touch gate is the only unlock authority. */
(()=>{'use strict';
document.addEventListener('selectstart',event=>event.preventDefault());
document.addEventListener('contextmenu',event=>event.preventDefault());
const $=id=>document.getElementById(id),app=$('app'),hero=$('hero'),root=document.body,ns='http://www.w3.org/2000/svg';
let state={},motion=false,nativeMotion=false,firstHome=true,lastPage='',lastBlocked=0,holdTotal=300000,holding=false,fogTimer=null,pendingFog=false,releaseTimer=null,lastDays='',lastJar=0,activeGameOpen=false;
function layer(id,markup){const s=document.createElementNS(ns,'svg');s.id=id;s.setAttribute('class','scene-layer');s.setAttribute('viewBox','0 0 500 470');s.setAttribute('preserveAspectRatio','xMidYMax slice');s.setAttribute('aria-hidden','true');s.innerHTML=markup;hero.append(s);return s;}
const dawn=$('valley').cloneNode(true);dawn.id='dawn';dawn.setAttribute('class','scene-layer');dawn.querySelectorAll('[id]').forEach(el=>el.id='dawn-'+el.id);dawn.querySelectorAll('*').forEach(el=>{for(const a of Array.from(el.attributes)){let v=a.value.replace(/url\(#([^)]*)\)/g,'url(#dawn-$1)');if(a.name==='href'&&v.startsWith('#'))v='#dawn-'+v.slice(1);if(v!==a.value)el.setAttribute(a.name,v);}});hero.append(dawn);
layer('breeze','<g opacity=".14" fill="var(--mist)"><path d="M-50 285 Q100 257 240 282T570 277v11Q370 298 210 289T-50 300Z"/><path d="M-30 352Q80 333 176 347T540 327v10Q310 366 165 357T-30 364Z"/></g><path d="M138 315q2-5-3-8t-1-10q3-6-1-9" fill="none" stroke="var(--mist)" stroke-width="2" opacity=".4"/><g fill="var(--deep)" opacity=".8"><path d="M465 435v-55l-15 35 11-4-15 17 16-3-14 14 14-4v12Z"/></g>');
layer('sparks','<g fill="#f5e9bc"><circle cx="112" cy="56" r="1.1"/><circle cx="418" cy="101" r="1.2"/><circle cx="285" cy="30" r="1"/></g><g fill="#d6e9a8"><circle cx="328" cy="366" r="1.6"/><circle cx="355" cy="351" r="1.25"/></g>');
layer('bird','<g fill="none" stroke="var(--trees)" stroke-width="1.3" stroke-linecap="round"><path d="M0 110q6-6 12 0 6-6 12 0"/><path class="return-birds" d="M-30 122q6-6 12 0 6-6 12 0M-50 100q5-5 10 0 5-5 10 0"/></g>');
const branches='M0 0V-47M0-12l-17-11M0-23l16-11M0-35l-10-9',crown='<path d="M0-77C-7-65-24-56-23-45c-10 8-8 24 3 26-7 10 6 19 20 16 14 3 27-6 20-16 11-2 13-18 3-26C24-56 7-65 0-77Z" fill="currentColor"/>';
layer('treeLayer','<g transform="translate(392 427)"><g id="growingTree" style="color:#9cbe8d">'+crown+'<path d="'+branches+'" fill="none" stroke="#c2c79b" stroke-width="2" stroke-linecap="round"/><g class="blossoms" fill="#f4d8b0"><circle cy="-53" r="4"/><circle cx="-13" cy="-35" r="3"/><circle cx="13" cy="-26" r="3.5"/><circle cx="6" cy="-64" r="3"/></g></g></g>');
hero.insertAdjacentHTML('beforeend','<div id="sunriseWash" aria-hidden="true"></div><div id="intercept" role="status"><strong>That can wait. Breathe.</strong><small id="interceptTime"></small></div><svg id="lantern" viewBox="0 0 80 110" aria-hidden="true"><defs><radialGradient id="lanternHalo"><stop stop-color="#ffd78b" stop-opacity=".7"/><stop offset="1" stop-color="#ffd78b" stop-opacity="0"/></radialGradient></defs><ellipse class="lantern-light" cx="40" cy="65" rx="40" ry="44" fill="url(#lanternHalo)"/><path d="M26 42v-7a14 14 0 0128 0v7M24 44h32l5 45H19ZM21 93h38M24 41h32" fill="none" stroke="#c8b587" stroke-width="2"/><path class="lantern-flame" d="M40 49c3 9 9 14 5 22-4 7-13 0-10-7 2-5 5-7 5-15Z" fill="#ffdb8a"/></svg><div id="setupSteps"><span id="adminStep">Guard</span><span id="accessStep">Access</span></div>');
$('ring').insertAdjacentHTML('afterbegin','<span class="ring-progress" aria-hidden="true"><span class="ring-half right"><span class="ring-arc" id="arcRight"></span></span><span class="ring-half left"><span class="ring-arc" id="arcLeft"></span></span></span>');$('ring').setAttribute('aria-label','Hold continuously to end this session');$('ring').setAttribute('aria-describedby','holdHelp');
$('weekSummary').insertAdjacentHTML('afterend','<div id="weekTrees" aria-hidden="true"></div>');
$('pause').insertAdjacentHTML('afterbegin','<svg id="sprig" viewBox="-27 -80 54 84" aria-hidden="true"><path d="M0 0V-66M0-10l-20-17M0-22l19-17M0-35l-13-13M0-44l11-14M0-53l-8-10" fill="none" stroke="currentColor" stroke-width="2.6" stroke-linecap="round"/></svg>');
const complete=document.createElement('section');complete.id='complete';complete.hidden=true;complete.innerHTML='<div class="small">A little space, reclaimed</div><h2>Unlocked</h2><p class="copy">Your session is complete.<br>Take this quiet with you.</p><button class="primary" data-act="exit">Exit</button><button class="secondary" data-act="continue">Back to Home</button>';document.querySelector('main').append(complete);
$('block').insertAdjacentHTML('beforeend','<div id="jarGame" hidden><div>Catch a little light<small id="jarHint">Five quiet taps. No score to chase.</small></div><button class="jar-button" data-act="jar" aria-label="Catch a firefly"><span class="jar-count" id="jarCount">○○○○○</span></button></div>');
$('active').insertAdjacentHTML('beforeend','<button type="button" class="secondary" id="activeGameToggle">Calm games</button>');
for(const b of document.querySelectorAll('#complete [data-act],#jarGame [data-act]'))b.onclick=()=>{if(!state.active&&!holding)act(b.dataset.act);};
function write(id,text){if($(id).textContent!==text)$(id).textContent=text;}
function clearFog(){clearTimeout(fogTimer);fogTimer=null;pendingFog=false;$('intercept').classList.remove('visible');}
function showFog(){clearTimeout(fogTimer);fogTimer=null;if(document.hidden){pendingFog=true;return;}pendingFog=false;$('intercept').hidden=false;requestAnimationFrame(()=>{$('intercept').classList.add('visible');});fogTimer=setTimeout(clearFog,2500);}
function useMotion(){motion=nativeMotion&&!document.hidden&&!matchMedia('(prefers-reduced-motion: reduce)').matches;root.classList.toggle('motion-off',!motion);if(!motion){if(document.hidden&&!pendingFog)clearFog();clearTimeout(releaseTimer);releaseTimer=null;root.classList.remove('releasing');}else if(pendingFog&&state.active){showFog();}}
window.setMotion=value=>{nativeMotion=!!value;useMotion();};document.addEventListener('visibilitychange',()=>{useMotion();if(document.hidden){holding=false;root.classList.remove('holding');cgStopAll();}else if(pendingFog&&state.active){showFog();}});matchMedia('(prefers-reduced-motion: reduce)').addEventListener('change',useMotion);
function progress(p,scenery=true){p=Math.max(0,Math.min(1,Number(p)||0));root.style.setProperty('--p',p);const right=$('arcRight'),left=$('arcLeft');right.style.transform='rotate('+Math.min(180,p*360)+'deg)';left.style.transform='rotate('+Math.max(0,(p-.5)*360)+'deg)';right.style.setProperty('--cap-gap',p>=.5?'0px':'1px');left.style.setProperty('--cap-gap',p>=1?'0px':'1px');if(!root.classList.contains('releasing')){right.style.opacity=p>0?'1':'0';left.style.opacity=p>.5?'1':'0';}if(scenery)root.style.setProperty('--scenic-p',p);}

/* Unified Calm Games widget: Focus Tiles, Memory Pairs, and Alpine Glide (Alto-inspired) */
const cgHost=document.createElement('div');cgHost.id='calmGames';
cgHost.innerHTML='<div class="cg-head"><div><div class="cg-title">Calm games</div><div class="cg-sub" id="cgSub">Quiet games to ride out an urge.</div></div><button type="button" class="cg-btn" id="cgToggle">Play</button></div><div id="cgBody" hidden><div class="cg-tabs" role="tablist" aria-label="Calm games"><button type="button" class="cg-tab" role="tab" data-game="tiles" aria-selected="true">Tiles</button><button type="button" class="cg-tab" role="tab" data-game="pairs" aria-selected="false">Pairs</button><button type="button" class="cg-tab" role="tab" data-game="glide" aria-selected="false">Alpine Glide</button></div><div class="cg-board g3" id="cgBoard"></div><div class="cg-foot"><div class="cg-status" id="cgStatus"></div><button type="button" class="cg-reset" id="cgReset">New</button></div></div>';
$('home').append(cgHost);
const cgBody=$('cgBody'),cgToggle=$('cgToggle'),cgBoard=$('cgBoard'),cgStatus=$('cgStatus'),cgReset=$('cgReset'),cgTabs=Array.from(cgHost.querySelectorAll('.cg-tab')),activeGameToggle=$('activeGameToggle');
let cgMode='tiles',cgTimers=[],cgLocked=false,cgGlideStop=null,cgGlideBest=0;
function cgClearTimers(){cgTimers.forEach(clearTimeout);cgTimers=[];}
function cgStopAll(){cgClearTimers();if(cgGlideStop){cgGlideStop();cgGlideStop=null;}}
function cgShuffle(arr){const a=arr.slice();for(let i=a.length-1;i>0;i--){const j=Math.floor(Math.random()*(i+1));[a[i],a[j]]=[a[j],a[i]];}return a;}

function cgStartTiles(){
 cgStopAll();cgBoard.className='cg-board g3';cgBoard.textContent='';
 const cells=[];for(let i=0;i<9;i++){const b=document.createElement('button');b.type='button';b.className='cg-cell';b.setAttribute('aria-label','Tile '+(i+1));cgBoard.append(b);cells.push(b);}
 let round=0,seq=[],step=0;
 function playRound(){
  cgClearTimers();cgLocked=true;step=0;cells.forEach(c=>c.classList.remove('lit','ok','err'));
  const len=3+round;seq=[];
  while(seq.length<len){const n=Math.floor(Math.random()*9);if(!seq.length||seq[seq.length-1]!==n)seq.push(n);}
  cgStatus.textContent='Round '+(round+1)+' of 4 · Watch';
  seq.forEach((idx,i)=>{
   cgTimers.push(setTimeout(()=>{cells[idx].classList.add('lit');},360+i*520));
   cgTimers.push(setTimeout(()=>{cells[idx].classList.remove('lit');if(i===seq.length-1){cgLocked=false;cgStatus.textContent='Repeat the '+len+' tiles';}},700+i*520));
  });
 }
 cells.forEach((cell,idx)=>{cell.onclick=()=>{
  if(cgLocked)return;
  if(idx===seq[step]){
   cell.classList.add('ok');cgTimers.push(setTimeout(()=>cell.classList.remove('ok'),200));
   step++;
   if(step===seq.length){
    cgLocked=true;round++;
    if(round>=4){cgStatus.textContent='Mind reset. Take a breath.';}
    else{cgStatus.textContent='Nice focus…';cgTimers.push(setTimeout(playRound,600));}
   }
  }else{
   cgLocked=true;cell.classList.add('err');cgStatus.textContent='Try that pattern again';
   cgTimers.push(setTimeout(()=>{cell.classList.remove('err');playRound();},600));
  }
 };});
 playRound();
}

function cgStartPairs(){
 cgStopAll();cgLocked=false;cgBoard.className='cg-board g4';cgBoard.textContent='';
 const deck=cgShuffle(['✦','✦','☽','☽','✿','✿','❋','❋','◈','◈','☀','☀']);
 let open=[],matched=0,moves=0;
 cgStatus.textContent='Find all 6 calm pairs';
 deck.forEach((sym,i)=>{
  const b=document.createElement('button');b.type='button';b.className='cg-cell';b.textContent='·';b.setAttribute('aria-label','Card '+(i+1));
  b.onclick=()=>{
   if(cgLocked||b.classList.contains('flipped')||b.classList.contains('ok'))return;
   b.classList.add('flipped');b.textContent=sym;open.push({b,sym});
   if(open.length<2)return;
   moves++;cgLocked=true;
   const [a,c]=open;open=[];
   if(a.sym===c.sym){
    cgTimers.push(setTimeout(()=>{
     a.b.classList.remove('flipped');c.b.classList.remove('flipped');
     a.b.classList.add('ok');c.b.classList.add('ok');
     matched++;cgLocked=false;
     cgStatus.textContent=matched>=6?'All 6 pairs matched in '+moves+' moves.':matched+' of 6 pairs · '+moves+' moves';
    },220));
   }else{
    cgTimers.push(setTimeout(()=>{
     a.b.classList.remove('flipped');c.b.classList.remove('flipped');
     a.b.textContent='·';c.b.textContent='·';cgLocked=false;
     cgStatus.textContent=matched+' of 6 pairs · '+moves+' moves';
    },560));
   }
  };
  cgBoard.append(b);
 });
}

/* Alpine Glide: Alto's Adventure–inspired downhill alpine runner with scarf, backflips, and light orbs (zero idle loop) */
function cgStartGlide(){
 cgStopAll();cgLocked=false;cgBoard.className='cg-board glide-wrap';cgBoard.textContent='';
 const cvs=document.createElement('canvas');cvs.className='cg-canvas';cvs.id='glideCanvas';
 cvs.setAttribute('aria-label','Alpine Glide game canvas. Tap to jump, hold in air to backflip.');
 cgBoard.append(cvs);
 const ctx=cvs.getContext('2d');
 const dpr=Math.min(2,window.devicePixelRatio||1);
 let W=300,H=196;
 function resize(){
  const rect=cvs.getBoundingClientRect();
  W=Math.max(240,Math.round(rect.width||300));
  H=196;
  cvs.width=Math.round(W*dpr);cvs.height=Math.round(H*dpr);
  ctx.setTransform(dpr,0,0,dpr,0,0);
 }
 resize();

 const stars=[[0.12,0.16,1.1],[0.24,0.11,0.9],[0.38,0.19,1.2],[0.52,0.09,1.0],[0.67,0.15,1.3],[0.79,0.22,0.9],[0.88,0.12,1.1],[0.31,0.28,0.8],[0.61,0.26,0.9]];
 const riderScreenX=76;
 let raf=0,lastTs=0,phase='ready'; // ready | run | over
 let worldX=0,speed=142,boost=0,score=0,orbsGot=0,flips=0;
 let ry=0,vy=0,grounded=true,coyote=0,angle=0,flipAccum=0,didFlip=false,pressing=false;
 let obstacles=[],orbs=[],puffs=[],scarf=[];
 let nextRockX=320,nextOrbX=210,banner='',bannerT=0;

 function terrainY(wx){
  return 134+Math.sin(wx*0.0085)*18+Math.sin(wx*0.019+1.2)*8;
 }
 function slopeAngle(wx){
  return Math.atan2(terrainY(wx+6)-terrainY(wx-6),12);
 }
 function addPuff(x,y,col,n){
  for(let i=0;i<n&&puffs.length<16;i++){
   puffs.push({x,y,vx:-30-Math.random()*55,vy:-25-Math.random()*45,r:2+Math.random()*2.4,a:0.85,col});
  }
 }
 function resetRun(){
  worldX=0;speed=142;boost=0;score=0;orbsGot=0;flips=0;
  ry=terrainY(riderScreenX);vy=0;grounded=true;coyote=0;angle=slopeAngle(riderScreenX);
  flipAccum=0;didFlip=false;pressing=false;
  obstacles=[];orbs=[];puffs=[];scarf=[];
  nextRockX=310;nextOrbX=200;banner='';bannerT=0;
  for(let i=0;i<7;i++)scarf.push({x:riderScreenX-i*5,y:ry-13});
  spawnAhead();
 }
 function spawnAhead(){
  const horizon=worldX+W+220;
  while(nextRockX<horizon){
   const r=8+Math.random()*4;
   obstacles.push({x:nextRockX,r});
   // Place a gentle arc of light orbs above some boulders
   if(Math.random()<0.65){
    orbs.push({x:nextRockX,y:terrainY(nextRockX)-38-Math.random()*12,got:false});
   }
   nextRockX+=185+Math.random()*125;
  }
  while(nextOrbX<horizon){
   const clear=obstacles.every(o=>Math.abs(o.x-nextOrbX)>36);
   if(clear)orbs.push({x:nextOrbX,y:terrainY(nextOrbX)-24-Math.random()*26,got:false});
   nextOrbX+=125+Math.random()*95;
  }
 }
 function stopLoop(){
  if(raf){cancelAnimationFrame(raf);raf=0;}
  pressing=false;
 }
 cgGlideStop=stopLoop;

 function drawMountainLayer(scroll,baseY,amp,freq,color,trees){
  ctx.fillStyle=color;ctx.beginPath();ctx.moveTo(0,H);
  for(let x=0;x<=W+12;x+=12){
   const wx=x+scroll;
   const y=baseY+Math.sin(wx*freq)*amp+Math.sin(wx*freq*2.3+0.8)*(amp*0.42);
   ctx.lineTo(x,y);
  }
  ctx.lineTo(W,H);ctx.closePath();ctx.fill();
  if(trees){
   const step=52,first=Math.floor(scroll/step)*step;
   for(let tx=first;tx<scroll+W+step;tx+=step){
    const hHash=Math.abs(Math.sin(tx*0.17));
    if(hHash<0.32)continue;
    const sx=tx-scroll;
    const ty=baseY+Math.sin(tx*freq)*amp+Math.sin(tx*freq*2.3+0.8)*(amp*0.42)+2;
    const th=11+hHash*10;
    ctx.beginPath();ctx.moveTo(sx,ty-th);ctx.lineTo(sx-5,ty+3);ctx.lineTo(sx+5,ty+3);ctx.closePath();ctx.fill();
   }
  }
 }

 function draw(){
  // Dusk-to-sage twilight sky
  const sky=ctx.createLinearGradient(0,0,0,H);
  sky.addColorStop(0,'#172f29');sky.addColorStop(0.55,'#2f4d43');sky.addColorStop(1,'#8da38c');
  ctx.fillStyle=sky;ctx.fillRect(0,0,W,H);

  // Subtle stars
  ctx.fillStyle='rgba(245,236,204,0.6)';
  for(const s of stars){ctx.beginPath();ctx.arc(s[0]*W,s[1]*H,s[2],0,Math.PI*2);ctx.fill();}

  // Glowing alpine moon/sun
  const mx=W*0.78,my=38;
  const glow=ctx.createRadialGradient(mx,my,4,mx,my,36);
  glow.addColorStop(0,'rgba(255,234,179,0.52)');glow.addColorStop(1,'rgba(255,234,179,0)');
  ctx.fillStyle=glow;ctx.beginPath();ctx.arc(mx,my,36,0,Math.PI*2);ctx.fill();
  ctx.fillStyle='#f7e6bd';ctx.beginPath();ctx.arc(mx,my,11,0,Math.PI*2);ctx.fill();

  // Parallax mountain ridges
  drawMountainLayer(worldX*0.14,88,20,0.008,'#2a443b',false);
  drawMountainLayer(worldX*0.34,108,15,0.012,'#1e362e',true);

  // Foreground rolling snow/sage slope
  ctx.fillStyle='#132620';ctx.beginPath();ctx.moveTo(0,H);
  for(let x=0;x<=W+6;x+=6)ctx.lineTo(x,terrainY(worldX+x));
  ctx.lineTo(W,H);ctx.closePath();ctx.fill();

  // Sunlit snow crest line
  ctx.strokeStyle='#c3d6ad';ctx.lineWidth=2.4;ctx.beginPath();
  for(let x=0;x<=W+6;x+=6){const y=terrainY(worldX+x);if(x===0)ctx.moveTo(x,y);else ctx.lineTo(x,y);}
  ctx.stroke();

  // Light orbs
  for(const o of orbs){
   if(o.got)continue;
   const sx=o.x-worldX;if(sx<-15||sx>W+15)continue;
   const og=ctx.createRadialGradient(sx,o.y,1,sx,o.y,10);
   og.addColorStop(0,'rgba(255,233,168,0.95)');og.addColorStop(1,'rgba(255,233,168,0)');
   ctx.fillStyle=og;ctx.beginPath();ctx.arc(sx,o.y,10,0,Math.PI*2);ctx.fill();
   ctx.fillStyle='#fff4d2';ctx.beginPath();ctx.arc(sx,o.y,3.2,0,Math.PI*2);ctx.fill();
  }

  // Alpine boulders
  for(const rock of obstacles){
   const sx=rock.x-worldX;if(sx<-20||sx>W+20)continue;
   const sy=terrainY(rock.x);
   ctx.fillStyle='#3a5449';ctx.beginPath();
   ctx.moveTo(sx-rock.r,sy+3);ctx.lineTo(sx-rock.r*0.55,sy-rock.r*0.95);
   ctx.lineTo(sx+rock.r*0.25,sy-rock.r*1.05);ctx.lineTo(sx+rock.r,sy+3);ctx.closePath();ctx.fill();
   ctx.fillStyle='#728d7f';ctx.beginPath();
   ctx.moveTo(sx-rock.r*0.55,sy-rock.r*0.95);ctx.lineTo(sx+rock.r*0.25,sy-rock.r*1.05);
   ctx.lineTo(sx+rock.r*0.05,sy-rock.r*0.25);ctx.closePath();ctx.fill();
  }

  // Snow/mist puffs
  for(const p of puffs){
   ctx.globalAlpha=Math.max(0,p.a);ctx.fillStyle=p.col;
   ctx.beginPath();ctx.arc(p.x,p.y,p.r,0,Math.PI*2);ctx.fill();
  }
  ctx.globalAlpha=1;

  // Flowing Alto-style scarf behind rider
  if(scarf.length>1){
   ctx.strokeStyle=boost>0?'#ffe3a3':'#e8a87c';ctx.lineCap='round';ctx.lineJoin='round';
   for(let i=0;i<scarf.length-1;i++){
    ctx.lineWidth=Math.max(1.2,4.2-i*0.52);
    ctx.globalAlpha=Math.max(0.2,1-i*0.12);
    ctx.beginPath();ctx.moveTo(scarf[i].x,scarf[i].y);ctx.lineTo(scarf[i+1].x,scarf[i+1].y);ctx.stroke();
   }
   ctx.globalAlpha=1;
  }

  // Rider silhouette + snowboard
  ctx.save();ctx.translate(riderScreenX,ry);ctx.rotate(angle);
  if(boost>0){
   ctx.fillStyle='rgba(255,227,163,0.22)';ctx.beginPath();ctx.arc(0,-9,16,0,Math.PI*2);ctx.fill();
  }
  // Snowboard
  ctx.strokeStyle='#e8f2d8';ctx.lineWidth=2.6;ctx.lineCap='round';
  ctx.beginPath();ctx.moveTo(-11,0);ctx.quadraticCurveTo(0,2,12,-1.5);ctx.stroke();
  // Rider cloak/body silhouette
  ctx.fillStyle='#f4eddd';
  ctx.beginPath();ctx.moveTo(-5,-1);ctx.lineTo(-1,-13);ctx.lineTo(4,-11);ctx.lineTo(3,-1);ctx.closePath();ctx.fill();
  ctx.beginPath();ctx.arc(1,-16,3.4,0,Math.PI*2);ctx.fill();
  ctx.restore();

  // Subtle HUD inside canvas
  ctx.fillStyle='rgba(244,237,221,0.88)';ctx.font='500 12px Outfit,sans-serif';ctx.textAlign='left';
  ctx.fillText(Math.floor(score)+' m',12,20);
  if(cgGlideBest>0){ctx.textAlign='right';ctx.fillStyle='rgba(195,214,173,0.78)';ctx.fillText('Best '+cgGlideBest+' m',W-12,20);}
  if(bannerT>0&&banner){
   ctx.textAlign='center';ctx.fillStyle='rgba(255,231,173,'+Math.min(1,bannerT*1.6)+')';
   ctx.font='500 13px Outfit,sans-serif';ctx.fillText(banner,W/2,44);
  }

  // Calm overlay prompt when ready or ended
  if(phase!=='run'){
   ctx.fillStyle='rgba(16,34,28,0.46)';ctx.fillRect(0,0,W,H);
   ctx.textAlign='center';ctx.fillStyle='#f4eddd';ctx.font='500 15px Outfit,sans-serif';
   ctx.fillText(phase==='ready'?'Tap to glide':'Nice run · '+Math.floor(score)+' m',W/2,H/2-6);
   ctx.fillStyle='#c3d6ad';ctx.font='400 12px Outfit,sans-serif';
   ctx.fillText(phase==='ready'?'Tap to jump · Hold in air to backflip':'Tap anywhere to glide again',W/2,H/2+15);
  }
 }

 function step(ts){
  if(phase!=='run'){raf=0;return;}
  const dt=Math.min(0.033,lastTs?(ts-lastTs)/1000:0.016);lastTs=ts;
  if(boost>0)boost=Math.max(0,boost-dt);
  if(bannerT>0)bannerT=Math.max(0,bannerT-dt);
  const curSpeed=speed+(boost>0?52:0);
  worldX+=curSpeed*dt;
  speed=Math.min(220,speed+dt*1.9);
  score+=curSpeed*dt*0.075;

  const groundY=terrainY(worldX+riderScreenX);
  const gSlope=slopeAngle(worldX+riderScreenX);

  if(grounded){
   ry=groundY;vy=0;coyote=0.095;
   angle+=(gSlope-angle)*Math.min(1,dt*14);
   flipAccum=0;didFlip=false;
  }else{
   coyote=Math.max(0,coyote-dt);
   vy+=590*dt;ry+=vy*dt;
   if(pressing){
    const spin=5.6*dt;angle-=spin;flipAccum+=spin;
    if(flipAccum>=Math.PI*1.82)didFlip=true;
   }else{
    // Gently level toward slope when not holding
    const norm=((angle-gSlope+Math.PI*3)%(Math.PI*2))-Math.PI;
    angle-=norm*Math.min(1,dt*3.2);
   }
   if(ry>=groundY){
    ry=groundY;vy=0;grounded=true;
    // Check clean landing (within ~65 deg of slope)
    const err=Math.abs((((angle-gSlope+Math.PI*3)%(Math.PI*2))-Math.PI));
    if(err>1.18){
     endRun('Landed off-balance');return;
    }
    if(didFlip){
     flips++;score+=5;boost=2.0;banner='Clean backflip! +5';bannerT=1.4;
     addPuff(riderScreenX,ry,'#ffe3a3',8);
    }else{
     addPuff(riderScreenX,ry,'#d8e6c8',4);
    }
    angle=gSlope;
   }
  }

  // Update scarf ribbon trail
  const neckX=riderScreenX-Math.sin(angle)*13,neckY=ry-Math.cos(angle)*13;
  scarf[0].x=neckX;scarf[0].y=neckY;
  for(let i=1;i<scarf.length;i++){
   scarf[i].x-=curSpeed*dt*0.42;
   scarf[i].x+=(scarf[i-1].x-6-scarf[i].x)*Math.min(1,dt*18);
   scarf[i].y+=(scarf[i-1].y+Math.sin(worldX*0.05+i)*1.4-scarf[i].y)*Math.min(1,dt*18);
  }

  // Collect light orbs
  const rwx=worldX+riderScreenX;
  for(const o of orbs){
   if(o.got)continue;
   const dx=o.x-rwx,dy=o.y-(ry-10);
   if(dx*dx+dy*dy<260){
    o.got=true;orbsGot++;score+=2;addPuff(o.x-worldX,o.y,'#ffe3a3',5);
   }
  }
  orbs=orbs.filter(o=>!o.got&&o.x>worldX-30);

  // Check boulder collisions (forgiving hitbox)
  for(const rock of obstacles){
   const dx=rock.x-rwx,sy=terrainY(rock.x);
   if(Math.abs(dx)<rock.r*0.72&&ry>sy-rock.r*0.82){
    endRun('Bumped a stone');return;
   }
  }
  obstacles=obstacles.filter(o=>o.x>worldX-40);

  // Update particles
  for(const p of puffs){p.x+=p.vx*dt;p.y+=p.vy*dt;p.a-=dt*1.9;}
  puffs=puffs.filter(p=>p.a>0);

  spawnAhead();
  cgStatus.textContent=Math.floor(score)+' m · '+orbsGot+' light'+(flips?' · '+flips+(flips===1?' flip':' flips'):'');
  draw();
  raf=requestAnimationFrame(step);
 }

 function endRun(reason){
  phase='over';stopLoop();
  const s=Math.floor(score);if(s>cgGlideBest)cgGlideBest=s;
  cgStatus.textContent=reason+' · '+s+' m'+(cgGlideBest?' (best '+cgGlideBest+' m)':'');
  draw();
 }

 function beginOrJump(){
  if(phase!=='run'){
   resetRun();phase='run';lastTs=0;
   cgStatus.textContent='Tap to jump · Hold in air to backflip';
   draw();raf=requestAnimationFrame(step);return;
  }
  if(grounded||coyote>0){
   grounded=false;coyote=0;vy=-248;flipAccum=0;didFlip=false;
   addPuff(riderScreenX,ry,'#d8e6c8',5);
  }
 }

 cvs.onpointerdown=e=>{
  e.preventDefault();if(holding)return;
  pressing=true;beginOrJump();
 };
 cvs.onpointerup=()=>{pressing=false;};
 cvs.onpointercancel=()=>{pressing=false;};
 cvs.onpointerleave=()=>{pressing=false;};

 resetRun();phase='ready';
 cgStatus.textContent='Tap to glide · Hold in air to backflip';
 draw();
}

function cgStart(mode){
 cgMode=mode||cgMode;
 cgTabs.forEach(t=>t.setAttribute('aria-selected',String(t.dataset.game===cgMode)));
 if(cgMode==='tiles')cgStartTiles();
 else if(cgMode==='pairs')cgStartPairs();
 else cgStartGlide();
}
function cgSyncHost(page){
 if(page==='session'){
  if(cgHost.parentNode!==$('active'))$('active').append(cgHost);
  cgHost.hidden=!activeGameOpen;
  cgBody.hidden=!activeGameOpen;
  cgToggle.hidden=true;
  root.classList.toggle('active-game-open',activeGameOpen);
  activeGameToggle.textContent=activeGameOpen?'Close calm games':'Calm games';
 }else{
  activeGameOpen=false;root.classList.remove('active-game-open');
  if(cgHost.parentNode!==$('home'))$('home').append(cgHost);
  cgHost.hidden=false;cgToggle.hidden=false;
  if(page!=='home'){cgStopAll();cgBody.hidden=true;cgToggle.textContent='Play';}
 }
}
cgTabs.forEach(tab=>{tab.onclick=()=>{cgStart(tab.dataset.game);};});
cgReset.onclick=()=>{cgStart(cgMode);};
cgToggle.onclick=()=>{
 if(!cgBody.hidden){cgStopAll();cgBody.hidden=true;cgToggle.textContent='Play';return;}
 cgBody.hidden=false;cgToggle.textContent='Hide';cgStart(cgMode);
};
activeGameToggle.onclick=()=>{
 if(holding)return;
 activeGameOpen=!activeGameOpen;
 cgSyncHost('session');
 if(activeGameOpen)cgStart(cgMode);else cgStopAll();
 reportRing();requestAnimationFrame(reportRing);
};

window.holdProgress=(p,left,total)=>{if(!state.active)return;holdTotal=total;holding=true;root.classList.add('holding');root.classList.remove('releasing');progress(p,false);write('holdTitle',clock(left,false));write('holdNote','Keep holding');};
window.sceneEvent=name=>{if(name==='hold-start'&&state.active){clearFog();cgStopAll();$('intercept').hidden=true;clearTimeout(releaseTimer);holding=true;root.classList.remove('releasing');root.classList.add('holding');write('holdNote','Keep holding');}if(name==='hold-lift'){holding=false;root.classList.remove('holding');root.classList.toggle('releasing',motion);progress(0);write('holdTitle',clock(holdTotal,false));write('holdNote','Hold to end');clearTimeout(releaseTimer);if(motion)releaseTimer=setTimeout(()=>{root.classList.remove('releasing');progress(0,false);},1500);}if(name==='setup-failed')root.classList.add('setup','setup-failed');};
window.times=(session,hold,pause)=>{write('clock',clock(session,true));write('interceptTime',clock(session,true)+' remaining');if(!holding){write('holdTitle',clock(holdTotal,false));write('holdNote','Hold to end');}if(app.dataset.page==='pause'){write('pauseTime',pause>0?clock(pause,false):'Time to check in');if(motion||pause<=0)root.style.setProperty('--pause-p',String(Math.max(.12,Math.min(1,1-pause/600000))));}};
function history(s,enter){const key=JSON.stringify(s.days||[]);if(key===lastDays&&!enter)return;lastDays=key;$('days').textContent='';$('weekTrees').textContent='';(s.days||[]).forEach((d,i)=>{const li=document.createElement('li'),label=document.createElement('strong'),value=document.createElement('span');label.textContent=d[0];value.textContent=d[1];if(d[3]){const tick=document.createElement('span');tick.className='session-tick';tick.textContent=' ✓';tick.setAttribute('aria-label',d[3]+' completed sessions');label.append(tick);}li.append(label,value);$('days').append(li);const day=document.createElement('div');day.className='day-tree '+(d[2]||'unknown')+(enter?' tree-enter':'');day.style.setProperty('--delay',(i*40)+'ms');const tree=document.createElementNS(ns,'svg');tree.setAttribute('viewBox','-27 -82 54 89');tree.innerHTML=(d[2]==='free'?crown:'')+'<path d="'+branches+'" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round"/>';const caption=document.createElement('span');caption.textContent=String(d[0]).slice(0,3);day.append(tree,caption);$('weekTrees').append(day);});}
window.screenState=s=>{
 state=s;holdTotal=s.holdDuration||300000;if(s.preview){root.classList.add('preview');root.dataset.scene='Dusk';app.dataset.page='preview';setMotion(!s.still);return;}
 const page=s.active?'session':s.page||'block',changed=page!==lastPage;root.classList.remove('preview');root.dataset.scene=s.active?'Night':page==='complete'?'Dawn':'Dusk';app.dataset.page=page;root.classList.toggle('setup',!!s.setup);root.classList.toggle('admin-ready',!!s.adminReady);root.classList.toggle('access-ready',!!s.accessReady);if(s.setup)root.classList.remove('setup-failed');setMotion(!s.still);
 for(const id of ['block','active','home','pause','week','complete'])$(id).hidden=id!==(page==='session'?'active':page);$('sessionSky').hidden=!s.active;$('bank').hidden=page==='complete';document.querySelector('nav').hidden=!!s.active||page==='complete';write('sceneLabel',s.setup?'Lantern light':page==='complete'?'Sunrise':s.active?'Night':'Quiet dusk');$('restore').hidden=!s.interrupted;write('guardStatus',s.guardMissing?'Uninstall guard was revoked in Android Settings.':s.interrupted?'Blocking access is off. Restore it to protect your session.':'');
 const minutes=Math.max(1,Math.min(1439,Number(s.minutes)||25)),h=Math.floor(minutes/60),m=minutes%60;write('sessionTotal',(h?h+' h ':'')+(m?m+' min ':'')+'session');$('durationValue').innerHTML=(h?h+' <span class="unit">h</span> ':'')+(m||!h?m+' <span class="unit">min</span>':'');write('selected',s.count?s.count+' selected':'Choose apps');$('start').firstChild.textContent=s.pending?'Continue setup':s.release?'Releasing access…':'Start blocking';$('start').disabled=!s.loaded||s.release;$('cancelSetup').hidden=!s.pending;write('status',s.pending?'Grant the requested permissions. Your timer starts when access connects.':'Selected apps and uninstall routes stay protected during your session.');write('reason',s.reason||'');write('step',s.step||'');write('pauseStep',s.pauseStep||'');write('pauseStart',s.paused?'Return to my pause':'Take a pause');const holdMin=Math.max(1,Math.round(holdTotal/60000));write('holdHelp','Keep one finger in the circle for '+holdMin+(holdMin===1?' minute.':' minutes.')+' Lifting resets the hold.');write('weekSummary',(s.free||0)+' porn-free days · '+(s.logged||0)+' days logged');if(page==='week')history(s,changed);document.querySelectorAll('nav button').forEach(b=>b.setAttribute('aria-current',String(b.dataset.act===(page==='pause'?'home':page))));
 cgSyncHost(page);
 const jar=$('jarGame').querySelector('button');if((s.jar||0)>lastJar&&motion){jar.classList.remove('jar-pop');void jar.offsetWidth;jar.classList.add('jar-pop');}lastJar=s.jar||0;$('jarGame').hidden=true;jar.disabled=lastJar>=5;write('jarCount','●'.repeat(Math.min(5,lastJar))+'○'.repeat(Math.max(0,5-lastJar)));write('jarHint',lastJar>=5?'A little light collected. Take a breath.':'Five quiet taps. No score to chase.');
 if(page==='block'&&firstHome&&!s.pending){firstHome=false;for(const [i,el] of [$('block').querySelector('.duration'),$('block').querySelector('.rows'),$('start')].entries()){el.classList.add('home-enter');el.style.setProperty('--delay',(i*60)+'ms');}$('start').classList.add('intro-glow');}
 if(page==='complete'&&changed){$('sunriseWash').classList.add('wash');holding=false;root.classList.remove('holding','releasing');root.style.setProperty('--scenic-p','1');complete.classList.add('complete-enter');}if(!s.active&&page!=='complete'){holding=false;root.classList.remove('holding','releasing');progress(0);}if(s.active&&changed){progress(0);write('holdTitle',clock(holdTotal,false));}if(s.active&&s.blockedSignal&&s.blockedSignal!==lastBlocked){lastBlocked=s.blockedSignal;showFog();}
 if((changed&&lastPage==='block')||!motion){document.querySelectorAll('.home-enter').forEach(el=>el.classList.remove('home-enter'));$('start').classList.remove('intro-glow');}lastPage=page;requestAnimationFrame(reportRing);
};
document.addEventListener('animationend',e=>{if(e.animationName==='sunrise-wash')$('sunriseWash').classList.remove('wash');if(e.animationName==='soft-rise')e.target.classList.remove('home-enter','complete-enter');if(e.animationName==='once-glow')$('start').classList.remove('intro-glow');if(e.animationName==='jar-catch')e.target.classList.remove('jar-pop');});
setMotion(false);
})();
