#!/usr/bin/env python3
"""Small local review UI for the V36 editorial pipeline (localhost only)."""
from __future__ import annotations
import argparse, json
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from editorial_catalogue import DECISIONS, classify

HTML = r'''<!doctype html><html><head><meta charset="utf-8"><meta name="viewport" content="width=device-width">
<title>Human V1 editorial workbench</title><style>body{font:16px system-ui;max-width:1100px;margin:auto;padding:24px;background:#f6f7fb;color:#17202a}header,.card{background:white;padding:20px;border-radius:14px;margin:12px 0;box-shadow:0 2px 12px #0001}label{display:block;font-weight:650;margin-top:12px}input,select,textarea,button{font:inherit;padding:10px;width:100%;box-sizing:border-box}textarea{min-height:110px}button{margin-top:16px;background:#3157d5;color:white;border:0;border-radius:8px}.pill{display:inline-block;background:#e9edff;padding:5px 9px;border-radius:20px;margin:3px}small{color:#566}</style></head><body>
<header><h1>Exercise editorial workbench</h1><p>Local review only. Recommendations are explainable and never publish or approve automatically.</p><label>Filter candidates<input id="filter"></label></header><main id="items"></main>
<script>let data={}; const esc=s=>String(s??'').replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
async function load(){data=await(await fetch('/api/state')).json();render()} function render(){let q=document.querySelector('#filter').value.toLowerCase();items.innerHTML=data.candidates.filter(c=>JSON.stringify(c).toLowerCase().includes(q)).map(c=>{let r=data.recommendations[c.candidateId],d=data.decisions[c.candidateId]||{};return `<section class=card><h2>${esc(c.exercise?.name||c.name)}</h2><small>${esc(c.candidateId)} · ${esc(c.lifecycle||'INGESTED')}</small><p><b>Recommendation:</b> ${esc(r.recommendation)} (${r.confidence})</p><p>${r.reasons.map(x=>`<span class=pill>${esc(x)}</span>`).join('')}</p><label>Decision<select id="kind-${esc(c.candidateId)}">${data.allowed.map(x=>`<option ${d.decision===x?'selected':''}>${x}</option>`).join('')}</select></label><label>Canonical target<input id="target-${esc(c.candidateId)}" value="${esc(d.canonicalExerciseId||r.canonicalExerciseId||'')}"></label><label>Review note<textarea id="note-${esc(c.candidateId)}">${esc(d.note||'')}</textarea></label><button onclick="save('${esc(c.candidateId)}')">Record reviewed decision</button></section>`}).join('')}
async function save(id){let body={candidateId:id,lifecycle:'REVIEWED',decision:document.querySelector(`#kind-${id}`).value,canonicalExerciseId:document.querySelector(`#target-${id}`).value||null,note:document.querySelector(`#note-${id}`).value};let res=await fetch('/api/decision',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify(body)});if(!res.ok)alert(await res.text());else await load()} filter.oninput=render;load();</script></body></html>'''

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
            if self.path != "/api/decision": self.send_error(404); return
            try:
                value=json.loads(self.rfile.read(int(self.headers.get("Content-Length","0"))))
                if value.get("decision") not in DECISIONS or value.get("lifecycle") != "REVIEWED": raise ValueError("invalid reviewed decision")
                existing=json.loads(decisions_path.read_text(encoding="utf-8")) if decisions_path.exists() else []
                existing=[d for d in existing if d.get("candidateId") != value.get("candidateId")]+[value]
                decisions_path.write_text(json.dumps(sorted(existing,key=lambda d:d["candidateId"]),indent=2)+"\n",encoding="utf-8"); self._json({"saved":True})
            except Exception as error: self._json({"error":str(error)},400)
        def log_message(self, *_): pass
    return Handler

def main():
    p=argparse.ArgumentParser(); p.add_argument("--candidates",required=True); p.add_argument("--catalogue",required=True); p.add_argument("--decisions",required=True); p.add_argument("--port",type=int,default=8765); a=p.parse_args()
    server=ThreadingHTTPServer(("127.0.0.1",a.port),create_handler(Path(a.candidates),Path(a.catalogue),Path(a.decisions)))
    print(f"Local editorial workbench: http://127.0.0.1:{a.port}"); server.serve_forever()
if __name__=="__main__": main()
