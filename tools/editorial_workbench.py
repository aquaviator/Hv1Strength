#!/usr/bin/env python3
"""Small local review UI for the V36 editorial pipeline (localhost only)."""
from __future__ import annotations
import argparse, json
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from editorial_catalogue import DECISIONS, build_release_draft, classify, validate_evidence, validate_rich_exercise

HTML = r'''<!doctype html><html><head><meta charset="utf-8"><meta name="viewport" content="width=device-width">
<title>Human V1 editorial workbench</title><style>body{font:16px system-ui;max-width:1100px;margin:auto;padding:24px;background:#f6f7fb;color:#17202a}header,.card{background:white;padding:20px;border-radius:14px;margin:12px 0;box-shadow:0 2px 12px #0001}label{display:block;font-weight:650;margin-top:12px}input,select,textarea,button{font:inherit;padding:10px;width:100%;box-sizing:border-box}textarea{min-height:110px}button{margin-top:16px;background:#3157d5;color:white;border:0;border-radius:8px}.pill{display:inline-block;background:#e9edff;padding:5px 9px;border-radius:20px;margin:3px}small{color:#566}</style></head><body>
<header><h1>Exercise editorial workbench</h1><p>Local review only. Recommendations are explainable and never publish or approve automatically.</p><label>Search candidates<input id="filter"></label><label>Editorial state<select id="state"><option>ALL</option><option>INGESTED</option><option>CLASSIFIED</option><option>REVIEWED</option><option>APPROVED</option><option>REJECTED</option></select></label><button onclick="draft()">Generate approved-only draft twice</button><pre id="draftResult"></pre></header><main id="items"></main>
<script>let data={}; const esc=s=>String(s??'').replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
async function load(){data=await(await fetch('/api/state')).json();render()} function render(){let q=filter.value.toLowerCase(),s=state.value;items.innerHTML=data.candidates.filter(c=>{let d=data.decisions[c.candidateId];return JSON.stringify(c).toLowerCase().includes(q)&&(s==='ALL'||(d?.lifecycle||c.lifecycle||'INGESTED')===s)}).map(c=>{let r=data.recommendations[c.candidateId],d=data.decisions[c.candidateId]||{},life=d.lifecycle||c.lifecycle||'INGESTED';return `<details class=card open><summary><b>${esc(c.exercise?.name||c.name)}</b> · ${esc(life)}</summary><p><b>Recommendation:</b> ${esc(r.recommendation)} (${r.confidence})</p><p>${r.reasons.map(x=>`<span class=pill>${esc(x)}</span>`).join('')}</p><p><b>Suggested matches:</b> ${r.alternatives.map(x=>`${esc(x.exerciseId)} (${x.score})`).join(', ')||'None'}</p><label>Decision<select id="kind-${esc(c.candidateId)}">${data.allowed.map(x=>`<option ${d.decision===x?'selected':''}>${x}</option>`).join('')}</select></label><label>Canonical target<input id="target-${esc(c.candidateId)}" value="${esc(d.canonicalExerciseId||r.canonicalExerciseId||'')}"></label><label>Exercise and evidence JSON<textarea id="exercise-${esc(c.candidateId)}">${esc(JSON.stringify(d.exercise||c.exercise||{},null,2))}</textarea></label><label>Review note<textarea id="note-${esc(c.candidateId)}">${esc(d.note||'')}</textarea></label>${life==='INGESTED'?`<button onclick="acceptClassification('${esc(c.candidateId)}')">Accept classification</button>`:''}${life==='CLASSIFIED'||life==='REVIEWED'?`<button onclick="save('${esc(c.candidateId)}')">Record reviewed decision</button>`:''}${life==='REVIEWED'&&d.decision!=='REJECT'?`<button onclick="approve('${esc(c.candidateId)}')">Approve reviewed decision</button>`:''}${life==='REVIEWED'&&d.decision==='REJECT'?`<button onclick="rejectCandidate('${esc(c.candidateId)}')">Confirm rejection</button>`:''}</details>`}).join('')}
async function save(id){let body={candidateId:id,lifecycle:'REVIEWED',decision:document.querySelector(`#kind-${id}`).value,canonicalExerciseId:document.querySelector(`#target-${id}`).value||null,exercise:JSON.parse(document.querySelector(`#exercise-${id}`).value),note:document.querySelector(`#note-${id}`).value};let res=await fetch('/api/decision',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify(body)});if(!res.ok)alert(await res.text());else await load()} async function move(id,path){let res=await fetch(path,{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({candidateId:id})});if(!res.ok)alert(await res.text());else await load()} async function acceptClassification(id){await move(id,'/api/classify')} async function approve(id){await move(id,'/api/approve')} async function rejectCandidate(id){await move(id,'/api/reject')} async function draft(){let res=await fetch('/api/draft',{method:'POST'});draftResult.textContent=JSON.stringify(await res.json(),null,2)} filter.oninput=render;state.onchange=render;load();</script></body></html>'''

def create_handler(candidates_path: Path, catalogue_path: Path, decisions_path: Path):
    class Handler(BaseHTTPRequestHandler):
        def _json(self, value, status=200):
            body=json.dumps(value, indent=2).encode(); self.send_response(status); self.send_header("Content-Type","application/json"); self.send_header("Content-Length",str(len(body))); self.end_headers(); self.wfile.write(body)
        def do_GET(self):
            if self.path == "/":
                body=HTML.encode(); self.send_response(200); self.send_header("Content-Type","text/html; charset=utf-8"); self.send_header("Content-Length",str(len(body))); self.end_headers(); self.wfile.write(body); return
            if self.path != "/api/state": self.send_error(404); return
            candidates=json.loads(candidates_path.read_text(encoding="utf-8")); catalogue=json.loads(catalogue_path.read_text(encoding="utf-8"))["exercises"]
            decisions=json.loads(decisions_path.read_text(encoding="utf-8")) if decisions_path.exists() else []
            self._json({"candidates":candidates,"recommendations":{c["candidateId"]:classify(c.get("exercise",c),catalogue) for c in candidates},"decisions":{d["candidateId"]:d for d in decisions},"allowed":DECISIONS})
        def do_POST(self):
            try:
                existing=json.loads(decisions_path.read_text(encoding="utf-8")) if decisions_path.exists() else []
                if self.path == "/api/decision":
                    value=json.loads(self.rfile.read(int(self.headers.get("Content-Length","0"))))
                    if value.get("decision") not in DECISIONS or value.get("lifecycle") != "REVIEWED": raise ValueError("invalid reviewed decision")
                    previous=next((d for d in existing if d.get("candidateId")==value.get("candidateId")),None)
                    if not previous or previous.get("lifecycle") not in {"CLASSIFIED","REVIEWED"}: raise ValueError("review requires a classified candidate")
                    if value["decision"] == "NEW_EXERCISE":
                        errors=validate_rich_exercise(value.get("exercise",{}))
                        if errors: raise ValueError("; ".join(errors))
                    if value["decision"] != "REJECT":
                        errors=validate_evidence(value.get("exercise",{}).get("evidence",[]))
                        if errors: raise ValueError("; ".join(errors))
                    existing=[d for d in existing if d.get("candidateId") != value.get("candidateId")]+[value]
                elif self.path == "/api/classify":
                    request=json.loads(self.rfile.read(int(self.headers.get("Content-Length","0"))))
                    if any(d.get("candidateId")==request.get("candidateId") for d in existing): raise ValueError("candidate already left INGESTED")
                    candidates=json.loads(candidates_path.read_text(encoding="utf-8")); catalogue=json.loads(catalogue_path.read_text(encoding="utf-8"))["exercises"]
                    candidate=next((c for c in candidates if c.get("candidateId")==request.get("candidateId")),None)
                    if not candidate: raise ValueError("unknown candidate")
                    recommendation=classify(candidate.get("exercise",candidate),catalogue)
                    existing.append({"candidateId":request["candidateId"],"lifecycle":"CLASSIFIED","recommendation":recommendation["recommendation"],"canonicalExerciseId":recommendation["canonicalExerciseId"]})
                elif self.path == "/api/approve":
                    request=json.loads(self.rfile.read(int(self.headers.get("Content-Length","0"))))
                    found=next((d for d in existing if d.get("candidateId")==request.get("candidateId")),None)
                    if not found or found.get("lifecycle") != "REVIEWED": raise ValueError("approval requires a reviewed decision")
                    existing=[d for d in existing if d is not found]+[{**found,"lifecycle":"APPROVED"}]
                elif self.path == "/api/reject":
                    request=json.loads(self.rfile.read(int(self.headers.get("Content-Length","0"))))
                    found=next((d for d in existing if d.get("candidateId")==request.get("candidateId")),None)
                    if not found or found.get("lifecycle") != "REVIEWED" or found.get("decision") != "REJECT": raise ValueError("rejection requires a reviewed reject decision")
                    existing=[d for d in existing if d is not found]+[{**found,"lifecycle":"REJECTED"}]
                elif self.path == "/api/draft":
                    candidates=json.loads(candidates_path.read_text(encoding="utf-8")); base=json.loads(catalogue_path.read_text(encoding="utf-8"))
                    first=build_release_draft(base,candidates,existing); second=build_release_draft(base,candidates,existing)
                    if first != second: raise ValueError("non-deterministic draft")
                    self._json({"exerciseCount":first["exerciseCount"],"payloadChecksum":first["payloadChecksum"],"deterministic":True,"publishedTransitions":0}); return
                else: self.send_error(404); return
                decisions_path.write_text(json.dumps(sorted(existing,key=lambda d:d["candidateId"]),indent=2)+"\n",encoding="utf-8"); self._json({"saved":True})
            except Exception as error: self._json({"error":str(error)},400)
        def log_message(self, *_): pass
    return Handler

def main():
    p=argparse.ArgumentParser(); p.add_argument("--candidates",required=True); p.add_argument("--catalogue",required=True); p.add_argument("--decisions",required=True); p.add_argument("--port",type=int,default=8765); a=p.parse_args()
    server=ThreadingHTTPServer(("127.0.0.1",a.port),create_handler(Path(a.candidates),Path(a.catalogue),Path(a.decisions)))
    print(f"Local editorial workbench: http://127.0.0.1:{a.port}"); server.serve_forever()
if __name__=="__main__": main()
