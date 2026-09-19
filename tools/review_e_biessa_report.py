"""Review the retained report without overwriting it; export a verifiable PDF.

Source of truth: the existing DOCX, JPA entities, services and recorded evaluations.
The PDF is typeset from the revised DOCX content; it is not a Word layout render.
"""
from pathlib import Path
from io import BytesIO
from copy import deepcopy
from html import escape
import re

from PIL import Image, ImageDraw
from docx import Document
from docx.text.paragraph import Paragraph as WordParagraph
from docx.table import Table as WordTable
from docx.shared import Cm, RGBColor
from docx.oxml.ns import qn
from reportlab.platypus import (SimpleDocTemplate, Paragraph, Spacer, PageBreak,
                               Image as RLImage, Table, TableStyle, KeepTogether)
from reportlab.platypus.tableofcontents import TableOfContents
from reportlab.lib.styles import getSampleStyleSheet, ParagraphStyle
from reportlab.lib import colors
from reportlab.lib.enums import TA_CENTER, TA_JUSTIFY
from reportlab.pdfbase import pdfmetrics
from reportlab.pdfbase.ttfonts import TTFont
from create_e_biessa_report import font, add_text, add_figure, add_table

ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / 'report_assets' / 'reviewed'
OUT = ROOT / 'deliverables'
ASSETS.mkdir(parents=True, exist_ok=True)
DOCX = OUT / 'Rapport_de_stage_E-biessa_revise.docx'
PDF = OUT / 'Rapport_de_stage_E-biessa_revise.pdf'
INK = '#20312d'
EDGE = '#48665c'


def canvas(height=1600):
    im = Image.new('RGB', (1700, height), 'white')
    return im, ImageDraw.Draw(im)


def label(d, xy, text, size=29):
    f = font(size)
    box = d.textbbox(xy, text, font=f)
    d.rectangle((box[0]-6, box[1]-3, box[2]+6, box[3]+3), fill='white')
    d.text(xy, text, font=f, fill=INK)


def cls(d, box, title, fields):
    x,y,r,b = box
    d.rectangle(box, fill='white', outline=INK, width=3)
    d.rectangle((x+2,y+2,r-2,y+61), fill='#e8f0ec')
    d.line((x,y+63,r,y+63), fill=INK, width=3)
    d.text((x+18,y+12), title, font=font(35,True), fill=INK)
    for i,field in enumerate(fields):
        d.text((x+18,y+80+i*42), field, font=font(31), fill=INK)


def link(d, points, labels=()):
    d.line(points, fill=EDGE, width=3)
    for x,y,t in labels:
        label(d,(x,y),t)


def classes():
    im,d=canvas()
    link(d,[(650,230),(980,230)],[(667,177,'1 vendeur'),(825,241,'0..*')])
    link(d,[(280,390),(280,650)],[(292,406,'1'),(292,600,'0..*')])
    link(d,[(650,765),(755,765),(755,425),(980,425)],[(663,713,'0..*'),(919,374,'1')])
    link(d,[(1320,500),(1320,690)],[(1332,515,'1'),(1332,642,'0..*')])
    link(d,[(1100,500),(855,500),(855,1110),(380,1110),(380,1200)],[(916,508,'0..*'),(392,1150,'0..*')])
    label(d,(525,1050),'compatibleVehicles')
    link(d,[(1500,500),(1670,500),(1670,1310),(1630,1310)],[(1552,512,'0..*'),(1510,1460,'0..1 catégorie')])
    link(d,[(70,230),(30,230),(30,1340),(70,1340)],[(42,1060,'0..*')])
    cls(d,(70,80,650,390),'User',['id : Long','email : String','role : UserRole','suspended : Boolean'])
    cls(d,(980,80,1630,500),'Part',['id : Long','title : String','price : BigDecimal','status : PartStatus','partPosition : String','oemNumber : String','vehicle : Vehicle [0..1]'])
    cls(d,(70,650,650,890),'Favorite',['id : Long','createdAt : LocalDateTime','{unique user, part}'])
    cls(d,(980,690,1630,930),'Image',['url : String','isPrimary : Boolean','sortOrder : Integer'])
    cls(d,(70,1200,650,1530),'Vehicle',['brand : String','model : String','year : Integer','engine : String','fuelType : String'])
    cls(d,(980,1200,1630,1430),'Category',['id : Long','name : String','parent : Category [0..1]'])
    label(d,(70,1560),'Préférences User ↔ Vehicle : 0..* ↔ 0..* en base ; au plus 5 côté acheteur.',28)
    im.save(ASSETS/'classes_catalogue.png')

    im,d=canvas(1650)
    link(d,[(650,220),(980,220)],[(665,169,'1 acheteur'),(813,232,'0..*')])
    link(d,[(650,335),(980,335)],[(665,287,'1 vendeur'),(813,347,'0..*')])
    link(d,[(1290,430),(1290,640)],[(1300,452,'0..*'),(1300,585,'1')])
    link(d,[(1060,430),(830,430),(830,715),(650,715)],[(889,439,'1'),(683,666,'0..*')])
    link(d,[(280,420),(280,640)],[(291,447,'1 expéditeur'),(291,590,'0..*')])
    link(d,[(280,890),(280,1130)],[(291,912,'1'),(291,1075,'0..*')])
    link(d,[(650,120),(900,120),(900,1000),(1110,1000),(1110,1130)],[(665,73,'1 acheteur'),(982,1008,'0..*')])
    link(d,[(650,385),(940,385),(940,1060),(1470,1060),(1470,1130)],[(665,396,'1 vendeur'),(1490,1080,'0..*')])
    cls(d,(70,80,650,420),'User',['id : Long','fullName : String','role : UserRole','email : String'])
    cls(d,(980,80,1630,430),'Conversation',['id : Long','createdAt : LocalDateTime','updatedAt : LocalDateTime','{unique part, buyer, seller}'])
    cls(d,(70,640,650,890),'Message',['body : String','createdAt : LocalDateTime','readAt : LocalDateTime'])
    cls(d,(980,640,1630,850),'Part',['id : Long','title : String','status : PartStatus'])
    cls(d,(70,1130,650,1460),'MessageAttachment',['url : String','originalName : String','contentType : String','sizeBytes : Long'])
    cls(d,(980,1130,1630,1470),'SellerReview',['rating : Integer {1..5}','comment : String [0..1]','updatedAt : LocalDateTime','{unique buyer, seller}'])
    label(d,(70,1520),'Chaque avis concerne un acheteur et un vendeur ; la conversation établit',29)
    label(d,(70,1565),"l’éligibilité métier, sans relation JPA directe entre avis et conversation.",29)
    im.save(ASSETS/'classes_messages.png')

    im,d=canvas(1590)
    for y,end in [(195,195),(315,730),(435,1260)]:
        x=740+(y-195)//120*70
        link(d,[(650,y),(x,y),(x,end),(980,end)],[(665,y-45,'1'),(885,end+8,'0..*')])
    link(d,[(270,470),(270,700)],[(283,482,'1'),(283,649,'0..*')])
    cls(d,(70,80,650,470),'User',['id : Long','email : String','password : String {haché}','role : UserRole','personalizationEnabled : Boolean'])
    cls(d,(980,80,1630,335),'ExternalIdentity',['provider : String','providerSubject : String','createdAt : LocalDateTime'])
    cls(d,(980,590,1630,915),'AuthExchangeCode',['codeHash : String','expiresAt : LocalDateTime','usedAt : LocalDateTime [0..1]'])
    cls(d,(980,1130,1630,1510),'PasswordRecoveryCode',['channel : RecoveryChannel','codeHash : String','expiresAt : LocalDateTime','usedAt : LocalDateTime [0..1]','attempts : int'])
    cls(d,(70,700,650,1015),'BuyerInteraction',['kind : Kind','targetId : Long','categoryId : Long','sellerId : Long','occurredAt : LocalDateTime'])
    cls(d,(70,1190,650,1510),'AdminAuditLog',['adminId : Long','action : String','targetType : String','targetId : Long','createdAt : LocalDateTime'])
    label(d,(70,1540),'AdminAuditLog conserve des valeurs de référence, pas une association JPA.',28)
    im.save(ASSETS/'classes_identity.png')

    im,d=canvas(1300)
    link(d,[(650,195),(980,195)],[(664,143,'1 cible'),(827,207,'0..*')])
    link(d,[(650,300),(980,300)],[(664,252,'1 auteur'),(827,312,'0..*')])
    link(d,[(650,410),(980,410)],[(664,362,'0..1 admin'),(827,422,'0..*')])
    link(d,[(200,460),(200,900)],[(212,480,'1 auteur'),(212,845,'0..*')])
    link(d,[(470,460),(470,900)],[(482,565,'0..1 admin'),(482,845,'0..*')])
    link(d,[(650,1040),(980,1040)],[(665,990,'0..*'),(900,1050,'1')])
    cls(d,(70,80,650,460),'User',['id : Long','role : UserRole','sellerApplicationStatus : Enum','sellerApplicationReviewedById','suspendedById : Long'])
    cls(d,(980,80,1630,500),'SellerReport',['reason : SellerReportReason','details : String','status : ReportStatus','adminNote : String [0..1]','resolvedAt : DateTime [0..1]'])
    cls(d,(70,900,650,1210),'ListingReport',['reason : ReportReason','details : String','status : ReportStatus','adminNote : String [0..1]'])
    cls(d,(980,900,1630,1140),'Part',['id : Long','title : String','status : PartStatus'])
    label(d,(70,1250),'La demande vendeur est portée par User ; aucune classe SellerApplication séparée.',27)
    im.save(ASSETS/'classes_moderation.png')


def message_sequence():
    from create_e_biessa_report import arrow
    im,d=canvas(900)
    xs=[180,650,1100,1510]
    for x,name in zip(xs,['Angular acheteur','Spring Boot','MySQL','Angular vendeur']):
        d.rectangle((x-158,60,x+158,140),fill='#e8f0ec',outline=INK,width=3)
        w=d.textlength(name,font=font(30,True))
        d.text((x-w/2,82),name,font=font(30,True),fill=INK)
        for y in range(150,850,22):d.line((x,y,x,y+12),fill=EDGE,width=2)
    events=[(230,180,650,'1 POST authentifié'),(345,650,1100,'2 Persister le message'),
            (465,1100,650,'3 Transaction validée'),(590,650,1510,'4 Événement WebSocket après validation'),
            (730,650,180,'5 Réponse HTTP')]
    for y,a,b,txt in events:
        arrow(d,(a,y),(b,y),color=EDGE,width=4)
        label(d,(min(a,b)+18,y-45),txt,29)
    label(d,(70,858),'Le canal WebSocket est établi au préalable avec un ticket court à usage unique.',29)
    im.save(ASSETS/'messaging_sequence.png')


def actor(d,x,y,name):
    d.ellipse((x-26,y-78,x+26,y-26),outline=INK,width=4)
    d.line([(x,y-26),(x,y+60)],fill=INK,width=4)
    d.line([(x-55,y+3),(x+55,y+3)],fill=INK,width=4)
    d.line([(x,y+60),(x-47,y+115)],fill=INK,width=4)
    d.line([(x,y+60),(x+47,y+115)],fill=INK,width=4)
    for i,line in enumerate(name.split('\n')):
        w=d.textlength(line,font=font(31,True))
        d.text((x-w/2,y+135+i*40),line,font=font(31,True),fill=INK)


def usecases(filename, groups):
    h=140+len(groups)*480
    im,d=canvas(h)
    d.rectangle((365,60,1660,h-20),outline=INK,width=3)
    d.text((400,75),'E-biessa',font=font(38,True),fill=INK)
    for i,(name,items) in enumerate(groups):
        y=165+i*480
        actor(d,160,y+140,name)
        for j,txt in enumerate(items):
            cy=y+j*105
            d.line([(215,y+140),(460,cy+42)],fill=EDGE,width=3)
            d.ellipse((460,cy,1590,cy+90),fill='#eef4f1',outline=INK,width=3)
            w=d.textlength(txt,font=font(34))
            d.text((1025-w/2,cy+23),txt,font=font(34),fill=INK)
    im.save(ASSETS/filename)


def insert_before(doc, anchor_text, callback):
    anchor=next(p for p in doc.paragraphs if p.text.startswith(anchor_text))._p
    before=set(doc.element.body)
    callback()
    for el in list(doc.element.body):
        if el not in before:
            anchor.addprevious(el)


def replace_start(doc, prefix, replacement):
    found=False
    for p in doc.paragraphs:
        if p.text.startswith(prefix):
            p.text=replacement
            found=True
    if not found: raise ValueError('Missing paragraph: '+prefix)


def figpage(doc,title,file,caption,text):
    p=doc.add_paragraph(title,style='Heading 3')
    p.paragraph_format.page_break_before=True
    add_figure(doc,ASSETS/file,caption,16)
    add_text(doc,text)


def revise():
    classes()
    message_sequence()
    usecases('usecases_buyers.png',[
        ('Visiteur',['Consulter les annonces et les profils vendeurs','Rechercher par filtres ou langage naturel','Créer un compte et se connecter']),
        ('Utilisateur\nconnecté',['Modifier son profil et gérer son compte','Identifier une pièce à partir d’une image','Consulter ses conversations et envoyer texte ou images']),
        ('Acheteur',['Sauvegarder des annonces et contacter un vendeur','Régler ses recommandations et ses véhicules par VIN','Évaluer ou signaler un vendeur et demander à vendre']),
    ])
    usecases('usecases_management.png',[
        ('Vendeur',['Créer et modifier une annonce avec ses compatibilités','Organiser les photos et prévisualiser la publication','Demander un prix indicatif et marquer la pièce vendue']),
        ('Administrateur',['Examiner les demandes de passage vendeur','Traiter les signalements et modérer les annonces','Suspendre des comptes et consulter le journal d’audit','Réindexer le catalogue pour la recherche IA']),
    ])
    doc=Document(OUT/'Rapport_de_stage_E-biessa.docx')
    # The retained file has a materialized TOC; do not mistake its entries for headings.
    for p in list(doc.paragraphs):
        if p.style.name.lower().startswith('toc '):
            p._p.getparent().remove(p._p)
    for t in doc.tables:
        if t.rows[0].cells[0].text != 'Acteur':
            continue
        for row in list(t.rows):
            if row.cells[0].text=='Service IA':
                t._tbl.remove(row._tr)
    cap=next(p for p in doc.paragraphs if p.style.name=='Caption Ebiessa' and p.text.startswith('Figure 4 - Séquence'))
    blip=cap._p.getprevious().find('.//'+qn('a:blip'))
    doc.part.related_parts[blip.get(qn('r:embed'))]._blob=(ASSETS/'messaging_sequence.png').read_bytes()
    replace_start(doc,"La version finale intègre aussi", "La version finale intègre aussi des recommandations personnalisées explicables, une orchestration LangChain en lecture seule, une assistance VIN, une estimation indicative des prix, une identification de pièces par image et une saisie vocale. La validation distingue les règles testées automatiquement des intégrations optionnelles qui exigent des services externes. L’application constitue un démonstrateur académique ; elle ne réalise ni paiement ni commande en ligne.")
    replace_start(doc,"The final solution uses", "The final solution uses Spring Boot for transactions and security, Angular for the user interface, and FastAPI for semantic retrieval and model-backed query understanding. A locally fine-tuned Qwen 2.5 7B model proposes automotive constraints, while deterministic catalogue rules remain authoritative. Explainable recommendations, read-only LangChain orchestration, VIN assistance, indicative pricing, optional CLIP image identification and editable voice input complete the academic demonstrator. Online payments and production deployment are outside its delivered scope.")
    replace_start(doc,"La réalisation a suivi", "La réalisation a suivi une démarche incrémentale. Chaque fonctionnalité a été développée, vérifiée et documentée avant l’introduction des fonctions dépendantes. Le socle marketplace a ainsi précédé la recherche sémantique, la personnalisation et les aides multimodales. Cette organisation ne suppose pas la tenue de cérémonies Scrum formelles.")
    replace_start(doc,"cycle de vie complet", "cycle de vie d’une annonce : saisie et aperçu avant publication, modification, activation, désactivation, vente et suppression ; les états persistés sont ACTIVE, INACTIVE et SOLD ;")
    replace_start(doc,"La couche LangChain coordonne", "La couche LangChain encapsule trois outils StructuredTool : search_agent, compatibility_agent et recommendation_agent. Le routage est déterministe : la recherche s’exécute d’abord, la vérification des contraintes suit si nécessaire, puis les candidats personnalisés fournis par Spring peuvent être intégrés selon l’intention détectée. Il ne s’agit pas d’agents autonomes qui planifient librement des actions. Aucun outil ne crée, ne modifie ou ne supprime une donnée métier.")
    replace_start(doc,"Après une interaction admissible", "Un acheteur ayant déjà ouvert une conversation avec un vendeur peut lui attribuer de une à cinq étoiles et ajouter un commentaire facultatif. Un seul avis est conservé par couple acheteur–vendeur et peut être mis à jour. Cette condition atteste un contact, pas un achat confirmé : aucune commande payée n’est enregistrée dans la plateforme. La moyenne et le nombre d’avis sont publics. Les signalements et leur résolution administrative sont gérés séparément.")
    replace_start(doc,"L'évaluation tenue à l'écart", "L’évaluation conservée dans training/v2-results-120.json porte sur 120 exemples tenus à l’écart de l’apprentissage. Elle mesure séparément la sortie brute de l’adaptateur et la chaîne corrigée. Un JSON syntaxiquement valide ne garantit pas la conformité du type de chaque champ, notamment celui de l’année.")
    replace_start(doc,"Après normalisation, le type", "Le type de pièce canonique atteint 99,2 %. En revanche, l’année brute obtient 0 % d’exactitude stricte et l’enregistrement brut complet obtient également 0 %. Après validation autoritaire de l’année explicite, l’exactitude de l’enregistrement complet atteint 96,7 % (116/120). Ce dernier chiffre caractérise donc la chaîne hybride, et non le modèle seul. Le petit jeu synthétique et les formes linguistiques qu’il couvre limitent toute généralisation à des utilisateurs réels.")
    replace_start(doc,"Le benchmark de récupération", "Le benchmark conservé dans training/retrieval-results.json comprend six requêtes synthétiques liées au catalogue de démonstration. HitRate@12, le rang réciproque moyen et l’exactitude des contraintes valent 1,0 sur ce jeu. Les six requêtes ne constituent ni une campagne statistique représentative ni une preuve d’efficacité en conditions réelles.")
    replace_start(doc,"Le premier chapitre présente", "Le premier chapitre présente le contexte et la démarche. Le deuxième formalise les besoins et les cas d’utilisation. Le troisième détaille l’architecture, les classes et les principaux échanges. Le quatrième décrit la réalisation, y compris les aides VIN, prix, image et voix. Le cinquième distingue les résultats des tests automatisés, l’évaluation du modèle et les limites expérimentales.")
    replace_start(doc,'7. Les messages et avis',"7. Les messages sont réservés aux participants autorisés ; les avis publiés sont consultables sur le profil vendeur.")
    p=next(p for p in doc.paragraphs if p.text.startswith('Le modèle exporté'))
    p.insert_paragraph_before("Le corpus corrigé v2 comprend 1 120 exemples d’apprentissage, 160 de validation et 120 de test. Les fichiers d’évaluation sont conservés séparément. La configuration locale limite la séquence à 256 tokens, utilise un lot de taille 1 et accumule les gradients sur huit étapes. Le taux d’apprentissage retenu pour l’époque v2 est de 0,00005.")

    for t in doc.tables:
        for row in t.rows:
            for c in row.cells:
                for p in c.paragraphs:
                    s=p.text
                    s=s.replace('50 tests réussis','62 tests réussis').replace('44 tests réussis','51 tests réussis').replace('20 tests réussis','28 tests réussis').replace('5 parcours réussis','7 parcours réussis')
                    s=s.replace('Aperçu, actif, inactif, vendu, modéré et supprimé','Aperçu avant envoi ; états ACTIVE, INACTIVE, SOLD ; suppression séparée')
                    s=s.replace("un index vectoriel obsolète ne peut pas rendre une annonce inactive", "un index obsolète ne doit pas exposer une annonce devenue inactive")
                    s=s.replace('Identité authentifiée et limite','Candidats assainis fournis par Spring et limite')
                    s=s.replace('Inclut recommandations, sécurité, réponses IA et limitation de débit','Rapports JUnit locaux : sécurité, recommandations, VIN et prix')
                    s=s.replace('Un avertissement de dépréciation non bloquant','Dernière campagne documentée ; CLIP simulé dans les tests')
                    s=s.replace('Inclut profil, interface, erreurs et recommandations','Dernière campagne documentée ; image et voix incluses')
                    s=s.replace('Scénarios opt-in','4 scénarios opt-in non exécutés')
                    s=s.replace('Séparation des profils local et production, identifiants non réutilisables','Comptes fictifs limités à la démonstration ; renouvellement avant diffusion')
                    s=s.replace("Disparition immédiate du catalogue et de l'index public","Masquage immédiat des résultats publics ; synchronisation de l’index après transaction")
                    s=s.replace('Compatible, incompatible ou inconnu','Candidats conservés après revalidation')
                    s=s.replace('Année universitaire','Année universitaire')
                    if s!=p.text: p.text=s

    def usecase_section():
        doc.add_paragraph('2.6 Diagrammes de cas d’utilisation',style='Heading 2')
        add_text(doc,"La frontière E-biessa comprend Angular, Spring Boot et le service FastAPI. Les acteurs humains déclenchent les opérations autorisées ; les services internes ne sont donc pas des acteurs externes dans ces diagrammes. Les vues regroupent les opérations proches pour conserver une lecture globale. Une association indique une interaction, pas une relation de succession.")
        figpage(doc,'Parcours de consultation et d’achat','usecases_buyers.png','Figure 1 - Cas d’utilisation de consultation et d’achat',"Les fonctions d’utilisateur connecté sont communes aux rôles authentifiés. Les coordonnées, les avis et les recommandations personnalisées restent soumis aux contrôles du rôle acheteur. L’expression « achat » désigne ici la recherche et la mise en relation, sans paiement intégré.")
        figpage(doc,'Publication et administration','usecases_management.png','Figure 2 - Cas d’utilisation de vente et d’administration',"Le vendeur bénéficie des fonctions communes de compte et de messagerie. L’administrateur possède également les droits de gestion des annonces autorisés par le backend. La suspension et la résolution des signalements donnent lieu à des traces d’audit.")
        doc.add_paragraph('Services externes et variantes',style='Heading 3')
        add_table(doc,['Service externe','Interaction avec E-biessa'],[
            ('Google, Facebook, Microsoft','Connexion OAuth quand le fournisseur est configuré.'),
            ('SMTP et Twilio','Acheminement des codes de récupération selon la configuration.'),
            ('CarQuery et NHTSA vPIC','Catalogue automobile et décodage VIN, avec limites de couverture.'),
            ('Cloudinary','Stockage optionnel des images à la place du stockage local.'),
            ('Service vocal du navigateur','Transcription soumise au support et aux permissions du navigateur.'),
        ],[5,10.8])
        add_text(doc,"Pour publier, le vendeur saisit les informations, consulte l’aperçu puis confirme l’envoi ; le serveur valide les champs et la propriété. Pour devenir vendeur, l’acheteur transmet sa demande et reste acheteur pendant l’examen. L’administrateur accepte ou refuse ; le rôle ne change qu’en cas d’acceptation. Pour contacter un vendeur, l’acheteur se connecte, ouvre une annonce active puis utilise les coordonnées autorisées ou crée une conversation.")
    insert_before(doc,'CHAPITRE 3',usecase_section)

    # Replace the old informal data-model picture and caption with actual UML views.
    oldcap=next(p for p in doc.paragraphs if p.style.name=='Caption Ebiessa' and p.text.startswith('Figure 2 - Vue simplifiée'))
    pic=oldcap._p.getprevious()
    pic.getparent().remove(pic)
    oldcap._p.getparent().remove(oldcap._p)
    def class_section():
        add_text(doc,"Les quatre vues suivantes forment le diagramme de classes du domaine persistant. Les noms correspondent aux entités Java ; les attributs sont sélectionnés pour la lisibilité. Les associations portent leurs multiplicités. Les rôles BUYER, SELLER et ADMIN sont les valeurs de UserRole, et non des sous-classes de User. Les méthodes métier appartiennent aux services, non aux entités représentées.")
        figpage(doc,'Catalogue et favoris','classes_catalogue.png','Figure 4 - Classes du catalogue et des favoris',"Part et Vehicle sont reliés par part_compatible_vehicles. Le lien historique Part.vehicle est maintenu séparément pour la compatibilité des données existantes. Category possède un parent facultatif et zéro à plusieurs sous-catégories. Les restrictions moteur et carburant de Part complètent les caractéristiques des véhicules ; elles ne constituent pas une entité de compatibilité distincte.")
        figpage(doc,'Conversations et réputation','classes_messages.png','Figure 5 - Classes de communication et de réputation',"Une conversation relie exactement une annonce, un acheteur et un vendeur. Elle contient zéro à plusieurs messages ; chaque message a un seul expéditeur et zéro à plusieurs pièces jointes. Les avis référencent directement les deux utilisateurs. Les autorisations vérifient que l’expéditeur appartient à la conversation.")
        figpage(doc,'Identité et personnalisation','classes_identity.png','Figure 6 - Classes d’identité et de personnalisation',"Les codes temporaires sont associés à un utilisateur et stockés sous forme de condensat. BuyerInteraction contient des références numériques de contexte et n’établit pas de relations JPA vers Part ou Category. AdminAuditLog conserve un instantané d’identifiants et de libellés pour préserver la trace d’une action.")
        figpage(doc,'Signalements et validation des vendeurs','classes_moderation.png','Figure 7 - Classes de modération',"SellerReport et ListingReport distinguent l’auteur, la cible et l’administrateur résolveur facultatif. La demande de passage vendeur est représentée par des champs de User, dont le statut et les dates de traitement. L’acceptation change le rôle ; le dépôt d’une demande ne crée pas automatiquement un vendeur.")
        add_text(doc,"Les structures de recherche, VIN, prix et vision sont des DTO ou des résultats temporaires de services. Elles ne créent pas de tables JPA supplémentaires. L’index Qdrant est reconstructible à partir de MySQL et n’est pas un second référentiel transactionnel. Les champs d’identité sont réservés aux DTO autorisés et ne sont pas tous exposés dans les profils publics.")
    insert_before(doc,'3.4 Conception',class_section)

    def new_features():
        doc.add_paragraph('4.10 Assistance VIN et catalogue automobile',style='Heading 2')
        add_text(doc,"Le profil acheteur propose la saisie d’un VIN de 17 caractères. Spring contrôle le format, interroge NHTSA vPIC puis rapproche la marque, le modèle, l’année et le carburant des lignes Vehicle du catalogue local. L’interface propose uniquement les identifiants locaux reconnus ; l’acheteur doit confirmer l’enregistrement de ses préférences. Une absence de correspondance conduit à la sélection manuelle, sans création automatique d’une compatibilité.")
        add_text(doc,"Le décodage utilise un délai configuré et un cache temporaire, fixé par défaut à 24 heures. Le VIN n’est pas persisté en base ; il est néanmoins transmis à NHTSA et intervient dans le cache de requête. La couverture vPIC, principalement orientée vers le marché américain, limite la reconnaissance de certains véhicules européens ou tunisiens. Le décodage n’est jamais présenté comme une garantie de montage.")
        doc.add_paragraph('4.11 Estimation indicative du prix',style='Heading 2')
        add_text(doc,"PriceEstimatorService compare la saisie du vendeur à un maximum de vingt annonces actives. Le score tient compte de la référence OEM, de la catégorie, des véhicules partagés, des termes du titre, de la position, de la marque de pièce et de l’état. L’annonce en cours d’édition et les vendeurs suspendus sont exclus. En l’absence de comparables suffisants, le système ne fabrique pas de prix.")
        add_text(doc,"La réponse fournit un prix central, une plage interquartile, le nombre de comparables et un niveau de confiance heuristique. Les quantiles de l’implémentation correspondent au rang arrondi dans la liste triée ; pour un effectif pair, le prix central n’est donc pas nécessairement la moyenne des deux valeurs centrales. Le calcul porte sur des prix demandés, pas sur des ventes conclues. Le vendeur conserve la décision et doit explicitement appliquer la suggestion.")
        doc.add_paragraph('4.12 Identification de pièces par image',style='Heading 2')
        add_text(doc,"L’utilisateur connecté peut transmettre une image JPEG, PNG ou WebP, limitée à 5 Mo. Spring applique l’authentification, la limitation de débit et les validations avant de transmettre les octets au service FastAPI avec le jeton interne. Le module optionnel utilise openai/clip-vit-base-patch32 pour comparer l’image à une taxonomie de vingt-deux types de pièces et retourner au plus cinq candidats.")
        add_text(doc,"Il s’agit d’une classification zero-shot, distincte de l’adaptation QLoRA de Qwen. Les scores sont relatifs aux catégories proposées, et non des probabilités calibrées de justesse. Le système ne déduit ni véhicule exact, ni référence OEM, ni garantie de compatibilité à partir des pixels. Le candidat choisi alimente une recherche dont les règles déterministes restent actives.")
        add_text(doc,"VISION_ENABLED reste désactivé par défaut. L’activation requiert les dépendances de vision et le téléchargement initial du modèle, mis en cache pour les requêtes suivantes. Aucune fonction métier ne conserve la photo d’identification ni ne l’utilise pour l’apprentissage ; le traitement multipart peut toutefois recourir à des fichiers temporaires selon la configuration du serveur. Les tests automatisés isolent l’inférence au moyen d’un substitut.")
        doc.add_paragraph('4.13 Saisie vocale',style='Heading 2')
        add_text(doc,"Angular exploite la reconnaissance vocale proposée par le navigateur avec un choix français de Tunisie, arabe de Tunisie ou anglais américain. Le texte reconnu reste modifiable et la recherche ne part qu’après confirmation. L’absence de microphone, le refus de permission ou l’absence de prise en charge laisse disponible la saisie au clavier.")
        add_text(doc,"E-biessa reçoit la transcription, pas l’enregistrement audio. Le navigateur peut cependant faire appel au service vocal de son fournisseur ; la fonctionnalité ne doit pas être décrite comme une reconnaissance entièrement locale. Les tests utilisent un objet de reconnaissance simulé ; la qualité acoustique dépend d’une vérification réelle sur le navigateur et le microphone retenus.")
    insert_before(doc,'CHAPITRE 5',new_features)

    def validation_note():
        add_text(doc,"Ces nombres décrivent les dernières campagnes consignées avant la revue documentaire, et non une nouvelle exécution de toutes les suites. Les rapports JUnit présents totalisent 62 tests sans échec. Les essais navigateur avec API simulée restent distincts des quatre scénarios de pile complète, désactivés par défaut. Ni ces comptes ni les simulations de CLIP et de microphone ne démontrent la précision de la vision ou de la reconnaissance vocale sur des données réelles.")
    insert_before(doc,'5.3 Évaluation',validation_note)
    def scope_note():
        doc.add_paragraph('5.7 Couverture du cahier des charges',style='Heading 2')
        add_table(doc,['Périmètre','Réalisation et portée'],[
            ('Phase 1 marketplace','Comptes, annonces, filtres, images, contact et administration ; transactions hors plateforme.'),
            ('Phase 2 intelligence artificielle','Embeddings, Qdrant, extraction Qwen adaptée et réponses déterministes vérifiées.'),
            ('Phase 3 orchestration','Outils LangChain en lecture seule, routage déterministe et recommandations explicables.'),
            ('Extensions proposées','VIN, prix indicatif, identification CLIP optionnelle et saisie vocale.'),
            ('Choix d’architecture','Spring Boot conservé pour le métier ; FastAPI consacré à l’IA, contrairement à un backend entièrement Python.'),
            ('Limites de validation','Pas d’étude utilisateur représentative ; vision, voix et pile complète à qualifier sur les services réels.'),
            ('Hors périmètre académique','Déploiement public, paiement, commandes, catalogue OEM certifié et garantie de compatibilité.'),
        ],[4.7,11.1])
        add_text(doc,"Le projet est achevé comme démonstrateur académique intégrant ces fonctions. La disponibilité des fournisseurs OAuth, du courriel, du SMS, de Cloudinary et de CLIP dépend des paramètres et des services activés. Le déploiement public, les migrations de schéma versionnées, la montée en charge et une étude utilisateur demeurent des travaux distincts ; ils ne sont pas présentés comme déjà validés.")
    insert_before(doc,'Conclusion générale',scope_note)
    def endpoints():
        doc.add_paragraph('Annexe D Compléments techniques',style='Heading 2')
        add_table(doc,['Fonction','Contrat ou paramètre'],[
            ('Décodage VIN','GET /api/vehicles/vin/{vin} ; authentification ; VIN_DECODER_ENABLED.'),
            ('Prix indicatif','POST /api/parts/price-estimate ; vendeur ou administrateur.'),
            ('Identification','POST /api/ai/identify ; fichier authentifié ; relais POST /v1/vision/identify.'),
            ('Activation vision','VISION_ENABLED ; requirements-vision.txt ; modèle CLIP mis en cache.'),
            ('Personnalisation','GET /api/recommendations ; GET/PUT /api/recommendations/settings ; DELETE /api/recommendations/history.'),
        ],[4.2,11.6])
        add_text(doc,"La traçabilité technique s’appuie sur PROJECT_HANDOFF.md, ARCHITECTURE.md, RECOMMENDATIONS.md, AGENT_ORCHESTRATION.md, VIN_COMPATIBILITY.md et IMAGE_AND_VOICE_SEARCH.md. Les classes figurées proviennent du paquet entity du backend. Les métriques du modèle et de récupération proviennent respectivement de training/v2-results-120.json et training/retrieval-results.json.")
    endpoints()

    # Correct numbering after adding use-case and class views, including the figure list.
    renames={
        'Figure 1 - Architecture globale de la solution':'Figure 3 - Architecture globale de la solution',
        'Figure 3 - Chaîne de recherche intelligente et réponse ancrée':'Figure 8 - Chaîne de recherche intelligente et réponse ancrée',
        'Figure 4 - Séquence simplifiée de la messagerie instantanée':'Figure 9 - Séquence simplifiée de la messagerie instantanée',
        'Figure 5 - Recommandation personnalisée explicable':'Figure 10 - Recommandation personnalisée explicable',
    }
    for p in list(doc.paragraphs):
        if p.text in renames: p.text=renames[p.text]
        if p.style.name.startswith('List') and p.text.startswith('Figure '): p._p.getparent().remove(p._p)
    tocfig=next(p for p in doc.paragraphs if p.text=='Liste des figures')
    captions=[p.text for p in doc.paragraphs if p.style.name=='Caption Ebiessa']
    for txt in reversed(captions):
        p=doc.add_paragraph(txt)
        tocfig._p.addnext(p._p)
    # Inline corrections retain surrounding document structure and original images.
    for p in doc.paragraphs:
        if p.style.name.startswith(('Heading','Title')):
            for run in p.runs: run.font.color.rgb=RGBColor(0,0,0)
        if p.text.startswith('4.4 Gestion complète'):
            p.paragraph_format.page_break_before=False
        if p.style.name=='Heading 2' and p.text.startswith(('3.4 Conception','3.7 Conception','3.8 Conception')):
            p.paragraph_format.page_break_before=True
    for name in ['Title','Heading 1','Heading 2','Heading 3']:
        doc.styles[name].font.color.rgb=RGBColor(0,0,0)
    # Recreate an updatable Word TOC after removing stale cached entries.
    from create_e_biessa_report import add_toc
    before=set(doc.element.body)
    add_toc(doc)
    sommaire=next(p for p in doc.paragraphs if p.text=='Sommaire')._p
    for el in list(doc.element.body):
        if el not in before: sommaire.addnext(el)
    doc.core_properties.subject='Rapport académique complet avec vues UML et évaluation de la chaîne hybride'
    doc.save(DOCX)
    return doc


def export_pdf(doc):
    # This is a separate typesetting pass, not a claim of Word-layout fidelity.
    for n,f in [('Body','arial.ttf'),('BodyBold','arialbd.ttf'),('BodyItalic','ariali.ttf')]:
        pdfmetrics.registerFont(TTFont(n,str(Path('C:/Windows/Fonts')/f)))
    pdfmetrics.registerFontFamily('Body',normal='Body',bold='BodyBold',italic='BodyItalic',boldItalic='BodyBold')
    styles=getSampleStyleSheet()
    styles.add(ParagraphStyle('Text',fontName='Body',fontSize=10.5,leading=15,spaceAfter=8,alignment=TA_JUSTIFY))
    styles.add(ParagraphStyle('DocTitle',fontName='BodyBold',fontSize=23,leading=29,spaceAfter=18))
    styles.add(ParagraphStyle('H1',fontName='BodyBold',fontSize=20,leading=25,spaceAfter=13,keepWithNext=True))
    styles.add(ParagraphStyle('H2',fontName='BodyBold',fontSize=14,leading=18,spaceBefore=13,spaceAfter=8,keepWithNext=True))
    styles.add(ParagraphStyle('H3',fontName='BodyBold',fontSize=12,leading=16,spaceBefore=11,spaceAfter=8,keepWithNext=True))
    styles.add(ParagraphStyle('Cap',fontName='BodyItalic',fontSize=9,leading=12,spaceAfter=10,alignment=TA_CENTER))
    styles.add(ParagraphStyle('Cell',fontName='Body',fontSize=9,leading=12))
    styles.add(ParagraphStyle('CellHead',fontName='BodyBold',fontSize=9,leading=12,textColor=colors.white))
    styles.add(ParagraphStyle('BulletE',parent=styles['Text'],leftIndent=12,firstLineIndent=-8))
    def clean(s): return escape(s).replace('\n','<br/>')
    story=[]
    def pb():
        if story and not isinstance(story[-1],PageBreak): story.append(PageBreak())
    toc=TableOfContents()
    toc.levelStyles=[ParagraphStyle('Toc1',fontName='BodyBold',fontSize=10,leading=15,spaceBefore=7),ParagraphStyle('Toc2',fontName='Body',fontSize=9,leading=13,leftIndent=14)]
    for el in doc.element.body:
        if el.tag==qn('w:sdt'): continue # stale Word table of contents
        if el.tag==qn('w:p'):
            p=WordParagraph(el,doc)
            txt=p.text.strip()
            name=p.style.name
            if name.lower().startswith('toc') or txt.startswith('La table des matières'): continue
            if txt.startswith('CHAPITRE'):
                pb(); continue
            if name=='Title' and txt and story and not txt.startswith('Conception et réalisation'): pb()
            if p.paragraph_format.page_break_before and not txt.startswith('Figure'): pb()
            imgs=el.findall('.//'+qn('a:blip'))
            for blip in imgs:
                part=doc.part.related_parts[blip.get(qn('r:embed'))]
                data=BytesIO(part.blob)
                im=Image.open(data);w,h=im.size
                maxh=468 if 'classes_' in str(part.partname) else 470
                scale=min(451/w,maxh/h)
                data.seek(0)
                story.append(RLImage(data,width=w*scale,height=h*scale))
            if txt:
                if txt=='Sommaire':
                    story.append(Paragraph(txt,styles['DocTitle']));story.append(toc);continue
                st='Text'
                if name=='Title':st='DocTitle'
                elif name.startswith('Heading 1'):st='H1'
                elif name.startswith('Heading 2'):st='H2'
                elif name.startswith('Heading 3'):st='H3'
                elif name=='Caption Ebiessa':st='Cap'
                elif name.startswith('List'):st='BulletE';txt='• '+txt
                par=Paragraph(clean(txt),styles[st])
                story.append(par)
            if el.findall('.//'+qn('w:br')) and any(b.get(qn('w:type'))=='page' for b in el.findall('.//'+qn('w:br'))): pb()
        elif el.tag==qn('w:tbl'):
            wt=WordTable(el,doc)
            rows=[[c.text.strip() for c in row.cells] for row in wt.rows]
            if not any(any(row) for row in rows):continue
            if len(rows)==1 and len(rows[0])==1:
                story.append(Paragraph(clean(rows[0][0]),styles['Text']));continue
            n=len(rows[0])
            widths=[451/n]*n
            if n==2:widths=[158,293]
            elif n==3:widths=[106,138,207]
            elif n==4:widths=[103,110,120,118]
            data=[[Paragraph(clean(v),styles['CellHead' if i==0 else 'Cell']) for v in row] for i,row in enumerate(rows)]
            tab=Table(data,colWidths=widths,repeatRows=1,hAlign='LEFT')
            tab.setStyle(TableStyle([
                ('BACKGROUND',(0,0),(-1,0),colors.HexColor('#314a56')),
                ('ROWBACKGROUNDS',(0,1),(-1,-1),[colors.white,colors.HexColor('#f1f4f5')]),
                ('GRID',(0,0),(-1,-1),0.5,colors.HexColor('#d9d9d9')),
                ('VALIGN',(0,0),(-1,-1),'MIDDLE'),('LEFTPADDING',(0,0),(-1,-1),8),('RIGHTPADDING',(0,0),(-1,-1),8),
                ('TOPPADDING',(0,0),(-1,-1),7),('BOTTOMPADDING',(0,0),(-1,-1),7),
            ]))
            story.extend([tab,Spacer(1,10)])
    paired=[]
    i=0
    while i<len(story):
        if isinstance(story[i],RLImage) and i+1<len(story) and isinstance(story[i+1],Paragraph) and story[i+1].style.name=='Cap':
            paired.append(KeepTogether([story[i],story[i+1]])); i+=2
        else:
            paired.append(story[i]); i+=1
    story=paired
    class Report(SimpleDocTemplate):
        def afterFlowable(self,flow):
            if isinstance(flow,Paragraph) and flow.style.name in ('H1','H2'):
                level=0 if flow.style.name=='H1' else 1
                self.notify('TOCEntry',(level,flow.getPlainText(),self.page))
    def pageframe(c,d):
        if d.page>1:
            c.setFont('Body',8);c.setFillColor(colors.HexColor('#56635e'))
            c.drawString(72,809,'E-biessa  |  Rapport de stage académique')
            c.drawRightString(523,29,str(d.page))
    report=Report(str(PDF),pagesize=(595.28,841.89),rightMargin=72,leftMargin=72,topMargin=60,bottomMargin=48,title='Rapport de stage E-biessa',author='Jasser Noomani')
    report.multiBuild(story,onFirstPage=pageframe,onLaterPages=pageframe)


if __name__=='__main__':
    revised=revise()
    export_pdf(revised)
    print(DOCX)
    print(PDF)
