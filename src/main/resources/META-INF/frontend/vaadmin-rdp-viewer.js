const codes = new Map();
for (const [start, keys] of [
  [1,'Escape Digit1 Digit2 Digit3 Digit4 Digit5 Digit6 Digit7 Digit8 Digit9 Digit0 Minus Equal Backspace Tab KeyQ KeyW KeyE KeyR KeyT KeyY KeyU KeyI KeyO KeyP BracketLeft BracketRight Enter ControlLeft KeyA KeyS KeyD KeyF KeyG KeyH KeyJ KeyK KeyL Semicolon Quote Backquote ShiftLeft Backslash KeyZ KeyX KeyC KeyV KeyB KeyN KeyM Comma Period Slash ShiftRight NumpadMultiply AltLeft Space CapsLock F1 F2 F3 F4 F5 F6 F7 F8 F9 F10 NumLock ScrollLock Numpad7 Numpad8 Numpad9 NumpadSubtract Numpad4 Numpad5 Numpad6 NumpadAdd Numpad1 Numpad2 Numpad3 Numpad0 NumpadDecimal'],
  [87,'F11 F12']
]) keys.split(' ').forEach((key,i)=>codes.set(key,[start+i,false]));
for (const [name,scan] of Object.entries({ControlRight:29,AltRight:56,MetaLeft:91,MetaRight:92,Home:71,ArrowUp:72,PageUp:73,ArrowLeft:75,ArrowRight:77,End:79,ArrowDown:80,PageDown:81,Insert:82,Delete:83,NumpadEnter:28,NumpadDivide:53})) codes.set(name,[scan,true]);

class VaadminRdpViewer extends HTMLElement {
  constructor() {
    super();
    const root = this.attachShadow({mode:'open'});
    root.innerHTML = `<style>:host{display:block;width:100%}section{display:flex;gap:.5rem;align-items:center;margin:.3rem 0}button{font:inherit;cursor:pointer}canvas{display:block;width:100%;height:auto;outline-offset:2px;touch-action:none}canvas[hidden],button[hidden]{display:none}</style><section><span role="status"></span><button hidden>复制远端文本</button><button hidden>复制到远端剪贴板</button></section><canvas tabindex="0" aria-label="Windows 远程桌面" hidden></canvas>`;
    this.canvas = root.querySelector('canvas');
    this.status = root.querySelector('[role=status]');
    this.buttons = [...root.querySelectorAll('button')];
    this.buttons[0].onclick = async () => {
      this.setStatus("正在复制远端文本");
      try { await navigator.clipboard.writeText(this.remoteText); this.canvas.focus({preventScroll:true}); this.setStatus("已复制到本机剪贴板"); }
      catch { this.setStatus('浏览器未允许写入剪贴板'); }
    };
    this.buttons[1].onclick = async () => {
      this.setStatus("正在更新远端剪贴板");
      try {
        await this.$server.offerText(await navigator.clipboard.readText());
        this.canvas.focus({preventScroll:true});
        this.setStatus("远端剪贴板已就绪，可按 Shift+Insert 粘贴");
      }
      catch { this.setStatus('浏览器未允许读取剪贴板，可在桌面中按 Ctrl+V'); }
    };
    this.remoteText = "";
    this.keys = new Set();
    this.generation = 0;
    this.canvas.addEventListener('keydown', e => this.keyboard(e,true));
    this.canvas.addEventListener('keyup', e => this.keyboard(e,false));
    this.canvas.addEventListener('blur', () => {
      for (const key of this.keys) this.$server.key(...codes.get(key),false);
      this.keys.clear();
    });
    this.canvas.addEventListener('paste', e => {
      const text = e.clipboardData.getData('text/plain');
      e.preventDefault();
      if (text) this.pasteText(text);
    });
    const buttons = [0x1000,0x4000,0x2000];
    this.canvas.addEventListener('pointerdown',e=>{
      if (!buttons[e.button]) return;
      this.canvas.focus(); this.canvas.setPointerCapture(e.pointerId);
      this.pointer(e,0x8000|buttons[e.button]); e.preventDefault();
    });
    this.canvas.addEventListener('pointerup',e=>{if(buttons[e.button])this.pointer(e,buttons[e.button]);});
    this.canvas.addEventListener('pointermove',e=>this.pointer(e,0x0800));
    this.canvas.addEventListener('contextmenu',e=>e.preventDefault());
    this.canvas.addEventListener('wheel',e=>{this.pointer(e,e.deltaY<0?0x0278:0x0388);e.preventDefault();},{passive:false});
  }

  async pasteText(text) {
    this.canvas.focus({preventScroll:true});
    await this.$server.offerText(text);
    await this.$server.pasteShortcut();
  }

  keyboard(e,down) {
    if (e.code==='KeyV' && (e.ctrlKey||e.metaKey)) {
      if (!navigator.clipboard?.readText) return;
      e.preventDefault();
      if (down && !e.repeat) {
        for (const modifier of ['ControlLeft','ControlRight','MetaLeft','MetaRight']) {
          if (this.keys.delete(modifier)) this.$server.key(...codes.get(modifier),false);
        }
        navigator.clipboard.readText().then(text=>this.pasteText(text))
          .catch(()=>this.setStatus('浏览器未允许粘贴，请检查剪贴板权限'));
      }
      return;
    }
    const key=codes.get(e.code);
    if (!key) return;
    e.preventDefault();
    if (down) this.keys.add(e.code);
    else this.keys.delete(e.code);
    this.$server.key(...key,down);
  }

  pointer(e,flags) {
    const bounds=this.canvas.getBoundingClientRect();
    const x=Math.max(0,Math.min(this.canvas.width-1,Math.floor((e.clientX-bounds.left)*this.canvas.width/bounds.width)));
    const y=Math.max(0,Math.min(this.canvas.height-1,Math.floor((e.clientY-bounds.top)*this.canvas.height/bounds.height)));
    this.$server.pointer(flags,x,y);
  }

  setRemoteText(text) { this.remoteText = text; }

  setStatus(text) { this.status.textContent=text; }

  openDesktop(width,height) {
    this.closeDesktop();
    const generation=this.generation;
    this.canvas.width=width; this.canvas.height=height; this.canvas.hidden=false;
    this.buttons.forEach(button=>button.hidden=false);
    this.canvas.focus(); this.setStatus('已连接');
    let revision=0;
    const poll=async()=>{
      if(generation!==this.generation || !this.isConnected) return;
      try {
        const frame=await this.$server.frame(revision);
        if(frame){
          const comma=frame.indexOf(',');
          const img=new Image();
          img.src='data:image/png;base64,'+frame.slice(comma+1);
          await img.decode();
          if(generation!==this.generation) return;
          this.canvas.getContext('2d').drawImage(img,0,0);
          revision=Number(frame.slice(0,comma));
        }
      } finally {
        if(generation===this.generation) this.timer=setTimeout(poll,120);
      }
    };
    poll();
  }

  closeDesktop() {
    this.generation++;clearTimeout(this.timer);this.keys.clear();this.remoteText="";
    this.canvas.hidden=true;this.buttons.forEach(button=>button.hidden=true);
    this.setStatus('已断开');
  }
  disconnectedCallback(){this.closeDesktop();}
}
customElements.define('vaadmin-rdp-viewer',VaadminRdpViewer);
