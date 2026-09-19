"""Build a standalone Overleaf ZIP from the reviewed report, without secrets.

Only report text and embedded illustration bytes are exported. No application
source, environment files, datasets, or user uploads enter the archive.
"""
from pathlib import Path
from zipfile import ZipFile, ZIP_DEFLATED
import hashlib
import re

from docx import Document
from docx.text.paragraph import Paragraph
from docx.table import Table
from docx.oxml.ns import qn

ROOT = Path(__file__).resolve().parents[1]
DEST = ROOT / 'deliverables' / 'E-biessa-Overleaf'
SOURCE = ROOT / 'deliverables' / 'Rapport_de_stage_E-biessa_revise.docx'
ZIP = ROOT / 'deliverables' / 'E-biessa-Overleaf.zip'

PREAMBLE = r'''% E-biessa academic report -- select pdfLaTeX and main.tex in Overleaf.
\documentclass[11pt,a4paper,oneside]{report}
\usepackage[T1]{fontenc}
\usepackage[utf8]{inputenc}
\usepackage{lmodern}
\usepackage[english,french]{babel}
\usepackage[margin=2.3cm,headheight=15pt]{geometry}
\usepackage{graphicx,adjustbox,float}
\usepackage[table]{xcolor}
\usepackage{array,longtable,booktabs}
\usepackage{enumitem}
\usepackage{microtype}
\usepackage{setspace}
\usepackage{titlesec}
\usepackage{fancyhdr}
\usepackage{xurl}
\usepackage[hidelinks,unicode]{hyperref}
\hypersetup{pdftitle={Rapport de stage E-biessa},pdfauthor={Jasser Noomani}}
\definecolor{tablehead}{HTML}{314A56}
\definecolor{tablerow}{HTML}{F1F4F5}
\definecolor{tableline}{HTML}{D9D9D9}
\setlength{\parindent}{0pt}
\setlength{\parskip}{6pt}
\setlength{\emergencystretch}{3em}
\setlength{\tabcolsep}{5pt}
\renewcommand{\arraystretch}{1.35}
\onehalfspacing
\setlist[itemize]{leftmargin=*,itemsep=3pt,topsep=5pt}
\setcounter{secnumdepth}{2}
\setcounter{tocdepth}{1}
\titleformat{\chapter}[display]{\normalfont\bfseries\LARGE}{\chaptertitlename\ \thechapter}{8pt}{\Huge}
\titlespacing*{\chapter}{0pt}{0pt}{18pt}
\pagestyle{fancy}
\fancyhf{}
\fancyhead[L]{\small E-biessa -- Rapport de stage académique}
\fancyfoot[C]{\thepage}
\renewcommand{\headrulewidth}{0pt}
\fancypagestyle{plain}{\fancyhf{}\fancyfoot[C]{\thepage}\renewcommand{\headrulewidth}{0pt}}
\begin{document}
\begin{titlepage}
\centering
{\large RAPPORT DE STAGE ACADÉMIQUE\par}
\vspace{1.2cm}
\includegraphics[width=.85\textwidth]{images/logo.png}
\vspace{1.2cm}
{\Huge\bfseries Conception et réalisation de E-biessa\par}
\vspace{.7cm}
{\Large Place de marché intelligente de pièces automobiles d'occasion\par}
\vspace{1.6cm}
\begin{tabular}{ll}
\textbf{Réalisé par} & Jasser Noomani\\[8pt]
\textbf{Domaine} & Génie logiciel et intelligence artificielle\\[8pt]
\textbf{Nature} & Projet académique de fin de stage\\[8pt]
\textbf{Année universitaire} & 2025--2026
\end{tabular}
\vfill
\end{titlepage}
\pagenumbering{roman}
'''


def tex(text):
    text=text.replace('\u00a0',' ').replace('\u202f',' ').replace('’',"'").replace('‘',"'")
    text=text.replace('–','--').replace('—','---').replace('‑','-')
    mapping={'\\':r'\textbackslash{}','&':r'\&','%':r'\%','$':r'\$',
             '#':r'\#','_':r'\_','{':r'\{','}':r'\}','~':r'\textasciitilde{}',
             '^':r'\textasciicircum{}','•':r'\textbullet{}', '↔':r'\(\leftrightarrow\)',
             '“':'``','”':"''",'…':r'\ldots{}'}
    # URLs use xurl's break opportunities; ordinary prose remains fully escaped.
    chunks=re.split(r'(https?://[^\s]+)',text)
    return ''.join(r'\url{'+c+'}' if c.startswith(('https://','http://')) else ''.join(mapping.get(a,a) for a in c) for c in chunks)


def table_latex(table):
    rows=[[c.text.strip() for c in row.cells] for row in table.rows]
    if not any(any(row) for row in rows):return ''
    if len(rows)==1 and len(rows[0])==1:
        bits=rows[0][0].split('\n',1)
        return '\\textbf{'+tex(bits[0])+'}\n\n'+(tex(bits[1]) if len(bits)>1 else '')+'\n\n'
    n=len(rows[0])
    fractions={2:[.35,.65],3:[.24,.32,.44],4:[.23,.25,.26,.26]}.get(n,[1/n]*n)
    cols=''.join(r'>{\raggedright\arraybackslash}p{\dimexpr '+f'{v:.3f}'+r'\textwidth-2\tabcolsep-2\arrayrulewidth\relax}|' for v in fractions)
    out=[r'{\small\singlespacing\arrayrulecolor{tableline}',r'\begin{longtable}{|'+cols+'}',r'\hline']
    header=r'\rowcolor{tablehead} '+ ' & '.join(r'\color{white}\bfseries '+tex(s) for s in rows[0])+r' \\ \hline'
    out.extend([header,r'\endfirsthead',header,r'\endhead',r'\hline\endfoot'])
    for i,row in enumerate(rows[1:]):
        out.append((r'\rowcolor{tablerow} ' if i%2 else '')+' & '.join(tex(s).replace('\n',r'\newline ') for s in row)+r' \\ \hline')
    out.extend([r'\end{longtable}',r'}',''])
    return '\n'.join(out)+'\n'


def main():
    DEST.mkdir(parents=True,exist_ok=True)
    (DEST/'images').mkdir(exist_ok=True)
    (DEST/'chapters').mkdir(exist_ok=True)
    doc=Document(SOURCE)
    streams={'00-frontmatter.tex':[]}
    current=streams['00-frontmatter.tex']
    started=False
    skip_figlist=False
    annex=False
    group=0
    figure_count=0
    image_index=0
    manifest=[]
    for el in doc.element.body:
        if el.tag==qn('w:p'):
            p=Paragraph(el,doc)
            t=p.text.strip()
            style=p.style.name
            if t=='Remerciements':started=True
            if not started:continue
            if style.lower().startswith('toc') or t.startswith('La table des matières'):continue
            if t=='Liste des figures':
                current.append('\\clearpage\n\\listoffigures\n')
                skip_figlist=True
                continue
            if skip_figlist:
                if t.startswith('Figure ') or not t:continue
                skip_figlist=False
            if t.startswith('CHAPITRE'):continue
            if t=='Sommaire':current.append('\\clearpage\n\\tableofcontents\n');continue
            if t=='Introduction générale':
                current.append('\\clearpage\n\\pagenumbering{arabic}\n')
            if style.startswith('Heading 1') or t.startswith('Annexe '):
                group+=1
                name=f'{group:02}-section.tex'
                streams[name]=[]
                current=streams[name]
                if t.startswith('Annexe '):
                    if not annex:current.append('\\appendix\n');annex=True
                    title=re.sub(r'^Annexe [A-Z]\s*[-–]?\s*','',t)
                    current.append('\\chapter{'+tex(title)+'}\n')
                elif re.match(r'^\d+\.',t):
                    current.append('\\chapter{'+tex(re.sub(r'^\d+\.\s*','',t))+'}\n')
                else:
                    current.append('\\chapter*{'+tex(t)+'}\n\\addcontentsline{toc}{chapter}{'+tex(t)+'}\n')
                continue
            if style=='Title':
                current.append('\\chapter*{'+tex(t)+'}\n\\addcontentsline{toc}{chapter}{'+tex(t)+'}\n')
                continue
            if style.startswith('Heading'):
                if p.paragraph_format.page_break_before:current.append('\\clearpage\n')
                level='section' if style.endswith('2') else 'subsection'
                title=re.sub(r'^\d+(?:\.\d+)*\s+','',t)
                current.append('\\'+level+'{'+tex(title)+'}\n')
                continue
            blips=el.findall('.//'+qn('a:blip'))
            for b in blips:
                image_index+=1
                part=doc.part.related_parts[b.get(qn('r:embed'))]
                filename=f'figure-{image_index:02}'+Path(str(part.partname)).suffix
                (DEST/'images'/filename).write_bytes(part.blob)
                manifest.append({'name':'images/'+filename,'sha256':hashlib.sha256(part.blob).hexdigest()})
                current.append('\\begin{figure}[H]\n\\centering\n\\begin{adjustbox}{max width=\\textwidth,max height=.64\\textheight}\n\\includegraphics{images/'+filename+'}\n\\end{adjustbox}\n')
            if style=='Caption Ebiessa':
                figure_count+=1
                caption=re.sub(r'^Figure \d+\s*[-–]\s*','',t)
                current.append('\\caption{'+tex(caption)+'}\n\\label{fig:report-'+str(figure_count)+'}\n\\end{figure}\n')
            elif t:
                if style.startswith('List'):
                    # Consecutive lists are merged in the final cleanup below.
                    current.append('\\begin{itemize}\n\\item '+tex(t)+'\n\\end{itemize}\n')
                else:current.append(tex(t)+'\n\n')
        elif el.tag==qn('w:tbl') and started:
            current.append(table_latex(Table(el,doc)))
    assert image_index==figure_count==10,(image_index,figure_count)
    for filename,parts in streams.items():
        text='\n'.join(parts)
        text=re.sub(r'\\end\{itemize\}\s*\\begin\{itemize\}','',text)
        (DEST/'chapters'/filename).write_text(text,encoding='utf-8')
    # Use the cover logo embedded in the report rather than a new application asset.
    for p in doc.paragraphs:
        blip=p._p.find('.//'+qn('a:blip'))
        if blip is not None:
            data=doc.part.related_parts[blip.get(qn('r:embed'))].blob
            (DEST/'images'/'logo.png').write_bytes(data)
            break
    main_text=PREAMBLE+'\n'.join('\\input{chapters/'+name+'}' for name in streams)+'\n\\end{document}\n'
    (DEST/'main.tex').write_text(main_text,encoding='utf-8')
    (DEST/'README.md').write_text('''# Rapport de stage E-biessa

Import the ZIP into Overleaf using New Project > Upload Project.
Main document: main.tex. Compiler: pdfLaTeX. Compile twice if references need updating.

The entire reviewed French report is editable in chapters/. The ten numbered
illustrations and cover logo are in images/. Class and use-case diagrams are
embedded PNG figures, not editable TikZ diagrams. The contents and figure list
are generated by LaTeX, so page numbers adapt to your edits.

This archive intentionally contains only report material. It contains no .env
files, credentials, private messages, model weights or application database.
The original DOCX and reviewed PDF remain outside this project.
''',encoding='utf-8')
    expected=[DEST/'main.tex',DEST/'README.md',*[(DEST/'chapters'/name) for name in streams],*sorted((DEST/'images').glob('*.png'))]
    # Only the explicit export file set is archived, never the surrounding repository.
    with ZipFile(ZIP,'w',ZIP_DEFLATED) as z:
        for f in expected:z.write(f,f.relative_to(DEST).as_posix())
    all_tex=main_text+'\n'+'\n'.join(''.join(p) for p in streams.values())
    for env in ['figure','longtable','adjustbox']:
        assert all_tex.count('\\begin{'+env+'}')==all_tex.count('\\end{'+env+'}')
    assert all((DEST/path).is_file() for path in re.findall(r'\\includegraphics(?:\[[^\]]*\])?\{([^}]+)\}',all_tex))
    print(f'{ZIP}\n{len(expected)} files; {figure_count} figures; {len(streams)} chapter files; {ZIP.stat().st_size} bytes')


if __name__=='__main__':main()
