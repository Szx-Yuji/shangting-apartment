import assert from 'node:assert/strict';
const base=process.env.TEST_BASE_URL||'http://127.0.0.1:8082';
if(!/^http:\/\/(127\.0\.0\.1|localhost):8082$/.test(base))throw new Error('Integration tests only target the complete project on loopback port 8082');
export class Client {
  cookies=new Map();
  csrf=null;
  csrfPending=null;
  async csrfToken(){
    if(this.csrf)return this.csrf;
    if(!this.csrfPending)this.csrfPending=this.request('GET','/api/csrf').then(response=>{
      assert.equal(response.status,200,'CSRF request failed');this.csrf=response.body;return this.csrf;
    }).finally(()=>{this.csrfPending=null;});
    return this.csrfPending;
  }
  async request(method,path,data,extra={}){
    const headers={...extra};
    if(!['GET','HEAD'].includes(method)){
      const csrf=await this.csrfToken();headers[csrf.headerName]=csrf.token;
    }
    if(this.cookies.size)headers.Cookie=[...this.cookies].map(([key,value])=>`${key}=${value}`).join('; ');
    let payload;
    if(data instanceof FormData)payload=data;
    else if(data!==undefined){headers['Content-Type']='application/json';payload=JSON.stringify(data);}
    const response=await fetch(base+path,{method,headers,body:payload,signal:AbortSignal.timeout(30_000)});
    for(const cookie of response.headers.getSetCookie()){
      const pair=cookie.split(';',1)[0],separator=pair.indexOf('=');
      const key=pair.slice(0,separator),value=pair.slice(separator+1);
      if(value)this.cookies.set(key,value);else this.cookies.delete(key);
    }
    const text=await response.text();let body;try{body=JSON.parse(text);}catch{body=text;}
    if(response.ok && /\/api\/(?:admin\/)?auth\/(?:register|login|logout)$/.test(path))this.csrf=null;
    return {status:response.status,body,headers:response.headers};
  }
}
export function status(response,expected,label){
  assert.equal(response.status,expected,`${label}: ${response.body?.detail||response.status}`);return response.body;
}
