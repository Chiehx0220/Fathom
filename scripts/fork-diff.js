const {execSync}=require("child_process");const fs=require("fs");
const path=require("path");
// Budget: how much of upstream's own code this fork has changed. Merge conflicts come from there, so the
// numbers may only go down. `--check` fails when one grows; `--update-budget` locks in a lower number.
const UPSTREAM="refs/remotes/upstream/main";
const BUDGET_FILE=path.join(__dirname,"fork-diff-budget.json");
const SCOPE=["app/src/main","build.gradle.kts","app/build.gradle.kts","gradle"];
function measure(){
 const git=c=>execSync(c,{encoding:"utf8",maxBuffer:1<<28,stdio:["ignore","pipe","ignore"]});
 // Measured from the last upstream commit this branch contains, so upstream moving ahead does not count as our change.
 const base=git(`git merge-base HEAD ${UPSTREAM}`).trim();
 const upstream=new Set(git(`git ls-tree -r --name-only ${base}`).split("\n"));
 const files=new Set();let lines=0,hunks=0,file=null;
 for(const l of git(`git diff ${base} -U0 --no-renames -- ${SCOPE.join(" ")}`).split("\n")){
  if(l.startsWith("diff --git"))file=l.split(" b/")[1];
  else if(!upstream.has(file))continue;
  else if(l.startsWith("@@")){hunks++;files.add(file);}
  else if((l[0]==="+"||l[0]==="-")&&!l.startsWith("+++")&&!l.startsWith("---"))lines++;
 }
 return {files:files.size,lines,hunks};
}
if(process.argv.includes("--check")||process.argv.includes("--update-budget")){
 const now=measure();
 const budget=fs.existsSync(BUDGET_FILE)?JSON.parse(fs.readFileSync(BUDGET_FILE,"utf8")):null;
 if(process.argv.includes("--update-budget")||!budget){
  fs.writeFileSync(BUDGET_FILE,JSON.stringify(now,null,2)+"\n");
  console.log(`Budget written: ${now.files} upstream files, ${now.hunks} hunks, ${now.lines} lines.`);
 }else{
  const over=Object.keys(now).filter(k=>now[k]>budget[k]);
  const under=Object.keys(now).filter(k=>now[k]<budget[k]);
  for(const k of Object.keys(now))console.log(`${k.padEnd(6)} ${String(now[k]).padStart(5)} / budget ${budget[k]}`);
  if(over.length){console.error(`Over budget: ${over.join(", ")}. Move the new code into a new file and leave only a call in the upstream file.`);process.exit(1);}
  if(under.length)console.log("Under budget: run `node scripts/fork-diff.js --update-budget` to lock it in.");
 }
 process.exit(0);
}
const sh=c=>execSync(c,{encoding:"utf8",maxBuffer:1<<26,stdio:["ignore","pipe","ignore"]});
const num=sh("git diff upstream/main --numstat --no-renames -- app/src/main app/src/test build.gradle.kts gradle app/build.gradle.kts").trim().split("\n").filter(Boolean).map(l=>{const [a,d,p]=l.split("\t");return {a:+a||0,d:+d||0,p}});
const isNew=p=>{try{sh(`git cat-file -e upstream/main:${p}`);return false}catch(e){return true}};
const rows=num.map(r=>({...r,isNew:isNew(r.p)}));
const area=p=>{
 if(/\/bilibili\//.test(p)||/Bilibili/.test(p)) return "Bilibili (native client, mappers, player/paging glue)";
 if(/io\/github\/aedev\/flow\/localserver\//.test(p)) return "Local server (fork-only feature)";
 if(/data\/recommendation\//.test(p)) return "FlowNeuro (Chinese text handling)";
 if(/^app\/src\/test\//.test(p)) return "Tests";
 if(/\.(xml)$/.test(p)) return "Resources / strings";
 if(/build\.gradle|libs\.versions|settings\.gradle/.test(p)) return "Build";
 return "Hooks in upstream files (serviceId plumbing, Bilibili branches, misc fork fixes)";
};
const groups={};
for(const r of rows){const g=area(r.p);(groups[g]=groups[g]||[]).push(r);}
let md=`# Where this fork differs from upstream

Generated from \`git diff upstream/main\`. Regenerate before a merge with:

\`\`\`
node scripts/fork-diff.js > FORK-DIFF.md
\`\`\`

The rows that matter for a merge are **modified upstream files** (upstream has the file, we changed lines
in it). **New files** never conflict; they are ours alone.

## Merging upstream

1. \`git fetch upstream\` and merge often - small merges conflict less than one big one.
2. \`git config rerere.enabled true\` is set in this clone: a conflict resolved once is resolved the same
   way next time.
3. After a merge, compile (\`gradlew.bat compileGithubNightlyKotlin\`). The likely breakage is in the
   "Hooks in upstream files" group below, not in the Bilibili or local server files.
4. Rules that keep the surface small: new code goes in a new file and upstream files only get a call to it;
   no reformatting or import reordering of upstream files.
5. \`node scripts/fork-diff.js --check\` compares the size of that surface with \`scripts/fork-diff-budget.json\`
   and fails when it grew; \`--update-budget\` lowers the budget after a cleanup.

`;
const order=["Hooks in upstream files (serviceId plumbing, Bilibili branches, misc fork fixes)","FlowNeuro (Chinese text handling)","Bilibili (native client, mappers, player/paging glue)","Local server (fork-only feature)","Build","Resources / strings","Tests"];
for(const g of order){
 const rs=(groups[g]||[]);if(!rs.length)continue;
 const mod=rs.filter(r=>!r.isNew).sort((a,b)=>(b.a+b.d)-(a.a+a.d));
 const neu=rs.filter(r=>r.isNew);
 const sum=x=>x.reduce((s,r)=>s+r.a+r.d,0);
 md+=`## ${g}\n\n- Modified upstream files: **${mod.length}** (${sum(mod)} changed lines). New files: **${neu.length}** (${sum(neu)} lines).\n\n`;
 if(mod.length){
  md+="| Modified upstream file | + | - |\n|---|---:|---:|\n";
  for(const r of mod.slice(0,g.startsWith("Hooks")?60:30)) md+=`| ${r.p.replace("app/src/main/java/io/github/aedev/flow/","").replace("app/src/main/java/","")} | ${r.a} | ${r.d} |\n`;
  if(mod.length>(g.startsWith("Hooks")?60:30)) md+=`| ... ${mod.length-(g.startsWith("Hooks")?60:30)} more, each small | | |\n`;
  md+="\n";
 }
}
process.stdout.write(md);
