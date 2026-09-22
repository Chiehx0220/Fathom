const {execSync}=require("child_process");const fs=require("fs");
const sh=c=>execSync(c,{encoding:"utf8",maxBuffer:1<<26,stdio:["ignore","pipe","ignore"]});
const num=sh("git diff upstream/main --numstat -- app/src/main app/src/test build.gradle.kts gradle app/build.gradle.kts").trim().split("\n").filter(Boolean).map(l=>{const [a,d,p]=l.split("\t");return {a:+a||0,d:+d||0,p}});
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
