import { Component, signal, provideZonelessChangeDetection, ViewChild, ElementRef, OnDestroy } from '@angular/core';
import { bootstrapApplication } from '@angular/platform-browser';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';

interface Actor {id:string;email:string;displayName:string;role:'CLIENT'|'SUPPORT'}
interface Conversation {id:string;ownerId:string;assignedAgentId:string|null;ownerName:string;status:string;version:number;eventCursor:number}
interface Message {id:string;sequence:number;clientMessageId:string;senderRole:string;text:string;createdAt:string;status:string}
interface Page {items:Message[];eventCursor:number;nextMessageCursor:number|null;hasMore:boolean}
interface Pending {id:string;text:string;conversation:string}
class ApiError extends Error {constructor(public status:number,title:string){super(title);}}

@Component({
  selector:'app-root',standalone:true,imports:[CommonModule,FormsModule],
  templateUrl:'./app.html'
})
export class App implements OnDestroy {
  user=signal<Actor|null>(null); conversations=signal<Conversation[]>([]); selected=signal<Conversation|null>(null);
  messages=signal<Message[]>([]); error=signal(''); announcement=signal(''); connection=signal('Hors connexion');
  busy=signal(false); sending=signal(false); pending=signal<Pending|null>(null); more=signal(false); loading=signal(true);
  username='';password='';draft=''; private stream?:EventSource;private generation=0;private eventCursor=0;
  private syncing=false;private syncAgain=false;private paused=false;
  @ViewChild('composer') composer?:ElementRef<HTMLTextAreaElement>;
  @ViewChild('history') history?:ElementRef<HTMLElement>;
  constructor(){void this.restore();}
  count(){return [...this.draft].length;}
  private async api<T>(path:string,method='GET',body?:unknown,headers:Record<string,string>={}):Promise<T>{
    const h:Record<string,string>={...headers};
    if(method!=='GET'){
      const cookie=document.cookie.split('; ').find(x=>x.startsWith('XSRF-TOKEN='));
      if(cookie)h['X-XSRF-TOKEN']=decodeURIComponent(cookie.slice(11));
    }
    if(body!==undefined && !(body instanceof URLSearchParams))h['Content-Type']='application/json';
    const timeout=new AbortController();const timer=setTimeout(()=>timeout.abort(),10000);
    try{
      const response=await fetch('/api/v1'+path,{method,credentials:'same-origin',headers:h,body:body===undefined?undefined:body instanceof URLSearchParams?body:JSON.stringify(body),signal:timeout.signal});
      if(!response.ok){let problem:any={};try{problem=await response.json();}catch{}
        if(response.status===401 && this.user()){this.disconnect();this.user.set(null);this.selected.set(null);this.messages.set([]);this.pending.set(null);}
        throw new ApiError(response.status,problem.title??'Action impossible ; vérifiez votre connexion');}
      return response.status===204?undefined as T:await response.json() as T;
    }finally{clearTimeout(timer);}
  }
  private async restore(){try{this.user.set(await this.api<Actor>('/session'));await this.api('/csrf');await this.refresh();}catch{}finally{this.loading.set(false);}}
  async login(){this.error.set('');this.busy.set(true);try{
    await this.api('/csrf');await this.api('/session','POST',new URLSearchParams({username:this.username,password:this.password}));
    this.password='';await this.api('/csrf');this.user.set(await this.api<Actor>('/session'));await this.refresh();
  }catch(e){this.error.set(this.reason(e));}finally{this.busy.set(false);}}
  async logout(){try{await this.api('/logout','POST');this.disconnect();this.generation++;this.user.set(null);this.selected.set(null);this.messages.set([]);this.pending.set(null);this.draft='';this.error.set('');await this.api('/csrf');}catch(e){this.error.set(this.reason(e));}}
  async refresh(){try{this.conversations.set(await this.api<Conversation[]>('/conversations'));}catch(e){this.error.set(this.reason(e));}}
  async create(){this.error.set('');this.busy.set(true);try{const c=await this.api<Conversation>('/conversations','POST',{}, {'Idempotency-Key':crypto.randomUUID()});await this.refresh();await this.select(c);}catch(e){this.error.set(this.reason(e));}finally{this.busy.set(false);}}
  canRead(c:Conversation){return c.ownerId===this.user()?.id || c.assignedAgentId===this.user()?.id;}
  async claim(c:Conversation){try{const mine=await this.api<Conversation>('/support/conversations/'+c.id+'/claim','POST');await this.refresh();await this.select(mine);}catch(e){this.error.set(this.reason(e));}}
  async select(c:Conversation){
    if(this.pending() && this.pending()?.conversation!==c.id){this.error.set('Vérifiez ou réessayez l’envoi incertain avant de changer de conversation.');return;}
    this.disconnect();const g=++this.generation;this.selected.set(c);this.messages.set([]);this.draft='';this.error.set('');this.announcement.set('');this.paused=false;this.eventCursor=0;
    try{const page=await this.api<Page>('/conversations/'+c.id+'/messages');if(g!==this.generation)return;
      this.messages.set(page.items);this.more.set(page.hasMore);this.eventCursor=page.eventCursor;this.connect(c.id,g);
      setTimeout(()=>this.composer?.nativeElement.focus(),0);
    }catch(e){this.error.set(this.reason(e));}
  }
  private connect(id:string,g:number){
    this.stream?.close();const stream=new EventSource('/api/v1/conversations/'+id+'/events?after='+this.eventCursor);this.stream=stream;this.connection.set('Connexion en cours');
    stream.onopen=()=>{if(g===this.generation){this.connection.set('Réception active');void this.catchUp(id,g);}};
    for(const kind of ['message.created','assigned','released','closed'])stream.addEventListener(kind,()=>{if(g===this.generation)void this.catchUp(id,g);});
    stream.onerror=()=>{if(g!==this.generation||this.paused)return;this.connection.set('Connexion interrompue — reprise automatique');
      void this.api<Conversation>('/conversations/'+id).then(c=>{if(g===this.generation)this.selected.set(c);}).catch(e=>{
        if(e instanceof ApiError && [401,403,404].includes(e.status)){stream.close();this.connection.set('Accès terminé');this.messages.set([]);this.error.set(e.message);}
      });};
  }
  private async catchUp(id:string,g:number){
    if(this.syncing){this.syncAgain=true;return;}this.syncing=true;
    try{do{this.syncAgain=false;let after=this.eventCursor;let page:Page;
      do{page=await this.api<Page>('/conversations/'+id+'/messages?afterSequence='+after);if(g!==this.generation)return;
        const existing=new Set(this.messages().map(m=>m.id));const added=page.items.filter(m=>!existing.has(m.id));
        if(added.length){this.messages.update(ms=>[...ms,...added].sort((a,b)=>a.sequence-b.sequence));this.announcement.set(added.length+' nouveau(x) message(s), disponibles dans l’historique.');}
        if(page.nextMessageCursor!==null)after=page.nextMessageCursor;
      }while(page.hasMore);
      if(g!==this.generation)return;this.eventCursor=page.eventCursor;
      const updated=await this.api<Conversation>('/conversations/'+id);if(g!==this.generation)return;this.selected.set(updated);
    }while(this.syncAgain && g===this.generation);
    }catch(e){if(g===this.generation)this.error.set(this.reason(e));}finally{this.syncing=false;}
  }
  async older(){const c=this.selected(),first=this.messages()[0];if(!c||!first)return;const id=c.id;
    const el=this.history?.nativeElement, height=el?.scrollHeight??0, top=el?.scrollTop??0;
    try{const page=await this.api<Page>('/conversations/'+id+'/messages?beforeSequence='+first.sequence);if(this.selected()?.id!==id)return;
      this.messages.update(ms=>[...page.items,...ms]);this.more.set(page.hasMore);
      setTimeout(()=>{if(el)el.scrollTop=top+el.scrollHeight-height;},0);
    }catch(e){this.error.set(this.reason(e));}
  }
  async send(){if(this.sending())return;if(this.pending()){this.error.set('Un envoi reste à vérifier : utilisez Réessayer le même envoi.');return;}
    const c=this.selected();if(!c)return;if(!this.draft.trim()||this.count()>2000){this.error.set('Écrivez entre 1 et 2 000 caractères.');return;}
    this.pending.set({id:crypto.randomUUID(),text:this.draft,conversation:c.id});await this.retry();}
  async retry(){const p=this.pending();if(!p||this.sending())return;this.sending.set(true);this.error.set('');
    try{const m=await this.api<Message>('/conversations/'+p.conversation+'/messages','POST',{clientMessageId:p.id,text:p.text});
      if(this.selected()?.id===p.conversation){if(!this.messages().some(x=>x.id===m.id))this.messages.update(ms=>[...ms,m].sort((a,b)=>a.sequence-b.sequence));if(this.draft===p.text)this.draft='';}
      this.pending.set(null);this.announcement.set('Votre message est enregistré.');
    }catch(e){
      // Definitive 4xx errors did not record a new message; transport errors remain uncertain.
      if(e instanceof ApiError && [400,401,403,404,409,422].includes(e.status))this.pending.set(null);
      this.error.set(this.reason(e)+(this.pending()?' Résultat incertain : réessayez le même envoi, sans changer son identifiant.':''));
    }finally{this.sending.set(false);}
  }
  toggleReception(){const c=this.selected();if(!c)return;this.paused=!this.paused;
    if(this.paused){this.stream?.close();this.connection.set('Réception suspendue pour la démonstration');}
    else this.connect(c.id,this.generation);
  }
  async close(){const c=this.selected();if(!c)return;try{this.selected.set(await this.api<Conversation>('/conversations/'+c.id+'/closure','POST',undefined,{'If-Match':String(c.version)}));await this.refresh();}catch(e){this.error.set(this.reason(e));}}
  async release(){const c=this.selected();if(!c)return;try{await this.api('/support/conversations/'+c.id+'/release','POST');this.disconnect();this.generation++;this.selected.set(null);this.messages.set([]);await this.refresh();}catch(e){this.error.set(this.reason(e));}}
  jump(){const el=this.history?.nativeElement;if(el){el.scrollTop=el.scrollHeight;el.focus();}}
  private disconnect(){this.stream?.close();this.stream=undefined;this.connection.set('Hors connexion');}
  private reason(e:unknown){return e instanceof Error?e.message:'Action impossible';}
  ngOnDestroy(){this.disconnect();}
}
bootstrapApplication(App,{providers:[provideZonelessChangeDetection()]}).catch(console.error);
