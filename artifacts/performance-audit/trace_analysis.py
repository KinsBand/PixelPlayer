from perfetto.trace_processor import TraceProcessor
from pathlib import Path
import json
root=Path(__file__).resolve().parent
tp=TraceProcessor(trace=str(root/'detailed.perfetto-trace'))
queries={
'processes': "select upid,pid,name from process where name like '%pixelplay%'",
'main_slices': """select s.name,count(*) n,round(sum(s.dur)/1e6,2) total_ms,round(max(s.dur)/1e6,2) max_ms from slice s join thread_track tt on tt.id=s.track_id join thread t on t.utid=tt.utid join process p on p.upid=t.upid where p.name='com.theveloper.pixelplay.debug' and t.is_main_thread=1 and s.dur>0 group by s.name order by max_ms desc limit 45""",
'thread_states': """select t.name,ts.state,round(sum(ts.dur)/1e6,2) ms from thread_state ts join thread t using(utid) join process p using(upid) where p.name='com.theveloper.pixelplay.debug' group by t.name,ts.state order by ms desc limit 40""",
'slow_main': """select s.id,s.name,round(s.ts/1e9,3) ts_s,round(s.dur/1e6,2) dur_ms from slice s join thread_track tt on tt.id=s.track_id join thread t on t.utid=tt.utid join process p on p.upid=t.upid where p.name='com.theveloper.pixelplay.debug' and t.is_main_thread=1 and s.dur>20000000 order by s.dur desc limit 35"""
}
out={}
for name,query in queries.items():
    out[name]=[vars(row) for row in tp.query(query)]
(root/'trace-analysis.json').write_text(json.dumps(out,indent=2))
print(json.dumps(out,indent=2))
tp.close()
