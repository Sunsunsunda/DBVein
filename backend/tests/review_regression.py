"""Run against an isolated DBVein instance on 18081 with .build-cache/review-test data."""
import json
import sqlite3
import urllib.request
import urllib.error
from pathlib import Path

BASE = "http://127.0.0.1:18081/api"
DB = Path(__file__).resolve().parents[2] / ".build-cache/review-test/dbvein.db"

def api(path, method="GET", body=None):
    request = urllib.request.Request(BASE + path, method=method,
        data=None if body is None else json.dumps(body).encode(), headers={"Content-Type": "application/json"})
    try:
        with urllib.request.urlopen(request, timeout=10) as response:
            raw = response.read()
            return response.status, json.loads(raw) if raw else None
    except urllib.error.HTTPError as error:
        return error.code, json.loads(error.read())

c = sqlite3.connect(DB)
p1 = api("/projects", "POST", {"name": "Synthetic A"})[1]["id"]
p2 = api("/projects", "POST", {"name": "Synthetic B"})[1]["id"]
w1 = c.execute("select id from analysis_workspace where project_id=?", (p1,)).fetchone()[0]
w2 = c.execute("select id from analysis_workspace where project_id=?", (p2,)).fetchone()[0]
def table(project, workspace, name):
    snapshot = c.execute("insert into snapshot(project_id,data_source_id) values (?,999)", (project,)).lastrowid
    c.execute("update analysis_workspace set active_snapshot_id=? where id=?", (snapshot, workspace))
    return c.execute("insert into table_meta(snapshot_id,schema_name,table_name) values (?,'demo',?)", (snapshot,name)).lastrowid

t1 = table(p1,w1,"parent")
t2 = table(p2,w2,"other")
s1 = c.execute("select snapshot_id from table_meta where id=?", (t1,)).fetchone()[0]
t3 = c.execute("insert into table_meta(snapshot_id,schema_name,table_name) values (?,'demo','child')", (s1,)).lastrowid
def column(table,name):
    return c.execute("insert into column_meta(table_id,column_name,nullable,ordinal_position,primary_key) values (?,?,0,1,0)", (table,name)).lastrowid
k1,k2,k3=column(t1,"id"),column(t2,"id"),column(t3,"parent_id")
r1=c.execute("insert into relation(workspace_id,source_table_id,source_column_id,target_table_id,target_column_id,relation_type,source_kind,status) values (?,?,?,?,?,'USER_DEFINED','USER','CONFIRMED')", (w1,t1,k1,t3,k3)).lastrowid
r2=c.execute("insert into relation(workspace_id,source_table_id,source_column_id,target_table_id,target_column_id,relation_type,source_kind,status) values (?,?,?,?,?,'USER_DEFINED','USER','CONFIRMED')", (w2,t2,k2,t2,k2)).lastrowid
c.commit()
root=f"/projects/{p1}/analysis"
assert api(root+"/canvas/tables", "POST", {"tableId":t2,"positionX":0,"positionY":0})[0]==400
assert api(root+"/relations/validate", "POST", {"sourceColumnId":k1,"targetColumnId":k2})[0]==400
assert api(root+"/relations", "POST", {"sourceColumnId":k1,"targetColumnId":k3})[0]==400
for action,method in [("/ignore","PUT"),("/confirm","PUT"),("","DELETE")]:
    assert api(root+f"/relations/{r2}"+action,method,{})[0]==400
assert api(root+"/relations/batch-ignore","POST",{"ids":[r1,r2]})[0]==400
assert c.execute("select status from relation where id=?",(r1,)).fetchone()[0]=="CONFIRMED"
assert api(root+"/canvas/tables","POST",{"tableId":t1,"positionX":1,"positionY":2})[0]==200
assert api(root+f"/canvas/tables/{t1}","DELETE")[0]==200
assert c.execute("select count(*) from relation where id=?",(r1,)).fetchone()[0]==1
assert api(root+"/canvas/tables","POST",{"tableId":t1,"positionX":1,"positionY":2})[0]==200
assert api(root+"/canvas","PUT",[])[0]==200
assert api(root+"/canvas")[1]==[]
print("PASS: project/table/column isolation; single and batch relation isolation; duplicate guard; removing nodes preserves relations; empty canvas persists")
c.close()
