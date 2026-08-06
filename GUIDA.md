# Electricity — guida al gioco

Come si costruisce un impianto elettrico che funziona, componente per componente.
Niente codice: solo quello che vedi e fai nel mondo.

---

## 1. Le tre cose da sapere prima di posare qualsiasi cosa

**La chiave inglese (Power Wrench) è lo strumento con cui si legge tutto.** Click destro su una
macchina qualsiasi del mod e si apre il suo pannello, o — se quella macchina non ha un pannello proprio —
una scheda con la potenza che ci passa. Puoi mirare anche da lontano: la chiave cerca la geometria della
macchina, non il blocco, quindi funziona anche sull'isolatore in cima a un traliccio.

**La mano vuota comanda, l'oggetto in mano no.** Ogni macchina che risponde a un click destro lo fa
**solo a mano vuota**: apri e chiudi un sezionatore, isoli un quadro di stringa, apri la schermata degli
sbracci di un palo. Se hai qualcosa in mano, il click va all'oggetto che tieni — così puoi tirare una
campata su un isolatore di un sezionatore senza aprirlo per errore, e puoi appoggiare un blocco contro un
palo senza aprire una GUI.

**Le macchine grandi occupano più di un blocco.** Un traliccio, un trasformatore da sottostazione, una
cabina, un palo, il quadro di controllo: occupano celle invisibili intorno a sé. Non ci puoi costruire
dentro, e rompendo una qualsiasi di quelle celle rompi la macchina. Le celle si rimettono a posto da sole:
se ne rompi una senza rompere la macchina ricompare, e se la macchina non c'è più le celle si liberano.

**Ogni macchina si orienta verso di te quando la posi.** Guardala mentre la piazzi: la faccia con la
porta, il pannello o le fasi guarderà verso il punto da cui l'hai posata.

---

## 2. Le due reti, che non si mescolano

Nel mod ci sono **due modi completamente diversi di collegare le cose**, ed è la prima cosa da capire.

### Corrente continua (DC) — il cavo si **posa a terra**

Serve dal pannello fotovoltaico fino all'inverter. Il cavo è un **blocco** che si posa come un
qualunque blocco: si collega da solo a quello che gli sta accanto, forma curve, incroci e scatole di
giunzione secondo come lo disponi. Le macchine "si trovano" seguendo il rame: se c'è una linea di cavo
continua da un pannello a un inverter, sono collegati. Non serve puntare nulla.

Due sezioni, e non sono interscambiabili:

| Cavo | Dove va |
|---|---|
| **Cabria SC-6** (cavo solare) | dal pannello al quadro di stringa, o dal pannello direttamente a un inverter di stringa |
| **Cabria DT-240** (dorsale DC) | dal quadro di stringa all'inverter, e **solo** lì |

Un inverter cerca i pannelli col cavo sottile e i quadri col cavo grosso. Se hai usato la sezione
sbagliata, l'inverter non vede niente e il suo pannello dice `nessuna stringa`.

**La lunghezza conta.** Ogni blocco di cavo è 10 metri. Il pannello del pannello fotovoltaico e quello del
quadro ti dicono quanti metri hai posato e quanta potenza stai perdendo nel rame. Un cavo interrato (posato
sotto il livello del terreno) perde uguale ma non si vede: la percentuale interrata è scritta nel pannello.

### Corrente alternata (AC) — la campata si **punta**

Serve da una macchina all'altra sopra terra. Prendi in mano una bobina di conduttore, **click destro su un
isolatore**, poi **click destro su un secondo isolatore**: la campata viene tirata fra i due. Il filo pende
con la sua catenaria e la potenza ci passa.

Tre conduttori, e ognuno va **solo** sulla sua classe di tensione:

| Conduttore | Classe | Campata massima | Blocchi per bobina |
|---|---|---|---|
| **Aerial Bundled Cable NFA2X 4×70** | bassa (1 kV) | 40 blocchi | 4 |
| **Aster 228** (conduttore MT) | media (24 kV) | 90 blocchi | 8 |
| **Curlew ACSR quadruplo** | alta (420 kV) | 160 blocchi | 16 |

Se sbagli classe il gioco te lo dice: *"Neither of those fittings is built for … Match the conductor to the
voltage."* Se la campata è troppo lunga, o non hai abbastanza bobina, te lo dice anche quello.

Gli stessi tre conduttori si possono anche **posare a terra** come blocchi, esattamente come i cavi DC:
serve per attraversare un cortile senza pali. Un tratto posato si collega solo a un tratto **della stessa
sezione**, e a pali, tralicci, cabine, kiosk e trasformatori.

---

## 3. Chi può alimentare chi

Questa è la mappa che decide se la potenza scorre. Vale per le campate AC.

```
                     turbina ─┐
                              ├─► trasformatore macchina ─┐
                    inverter ─┘                           │
                                                          ├─► cabina ─┐
        (tratto posato a terra) ◄────────────────────────►│           │
                                                          │           ▼
                                    trasformatore sottostazione ──► traliccio ──► traliccio …
                                                                        │
                                                          palo ◄────────┘
                                                            │
                                                            ▼
                                                          kiosk (Power Box) ──► FE
```

* Una **turbina** alimenta: cabina, un'altra turbina, un trasformatore macchina.
* Un **inverter** alimenta: cabina, un altro inverter, un trasformatore macchina.
* Un **trasformatore macchina** alimenta: cabina, un altro trasformatore, un tratto posato.
* Un **trasformatore da sottostazione** alimenta: traliccio, tratto posato. E niente altro: da lì parte la
  linea di trasmissione.
* Un **traliccio** alimenta: altro traliccio, cabina, palo, tratto posato, trasformatore.
* Un **palo** alimenta: altro palo, kiosk, tratto posato.
* Un **kiosk** non alimenta nessuno: è la fine della linea, e converte in FE.
* Un **sezionatore o interruttore** è trasparente: la potenza gli passa attraverso in entrambi i sensi
  quando è chiuso, e si ferma quando è aperto. Si mette **in mezzo** a una linea, mai in fondo.

---

## 4. Il fotovoltaico, componente per componente

### 4.1 I pannelli (Meridian) — sei prodotti, quattro modi di montarli

Ogni blocco è una **fila** di moduli, non un modulo singolo.

| Prodotto | Montaggio | Cosa fa |
|---|---|---|
| **FT-415**, **FT-430** | tavolo zavorrato, piatto | non si muove, nessuna parte mobile |
| | | *(la FT-415 è il vecchio "Solar Panel": è la stessa macchina, col nome nuovo)* |
| **TR-530**, **TR-580** | rastrelliera a inclinazione fissa | non si muove, inclinata verso l'equatore |
| **HX-700** | inseguitore a un asse | ruota da est a ovest seguendo il sole |
| **AE-440** | inseguitore a due assi | ruota e si inclina, segue il sole in tutto |

**Collegamento:** posa un cavo **SC-6** che tocchi la fila e portalo fino a un quadro di stringa o a un
inverter. Il pannello del pannello ti dice `Wired` / `Not wired`.

**Pannello (chiave inglese):**
* la targa del prodotto e la potenza di picco;
* l'irraggiamento sul piano dei moduli, diviso in **diretto**, **diffuso** e **riflesso dal terreno**, e
  la quota che i moduli usano davvero;
* la potenza disponibile e quella consegnata;
* per un inseguitore, la barra dell'angolo: la fascia verde è la corsa che il motore ha, la lancetta chiara
  è dov'è arrivato, quella trasparente è dove sta andando;
* i tre pulsanti dell'inseguitore: **automatico** (segue il sole), **a mano** (usi il cursore sotto),
  **in bandiera** (si mette in sicurezza);
* la riga delle perdite: **sporco**, **neve**, **cielo** visibile, **ombra delle file** davanti;
* la riga delle condizioni: temperatura di cella, aria, vento, angolo di incidenza;
* la riga del cablaggio: tensione e corrente di stringa;
* la riga della linea: metri posati, quanta parte è interrata, quanto si perde nel rame.

**Stati che vedi nel mondo:** un inseguitore che si muove lo vedi muoversi. In bandiera si mette piatto.
Con vento forte o neve va in bandiera **da solo** e il pannello dice perché.

### 4.2 Il quadro di stringa (Volterra CB-6 / CB-16 / CB-32)

Raccoglie tante stringhe sottili su una sola dorsale grossa. Sei, sedici o trentadue vie fusibilate.

**Collegamento:** cavi **SC-6** dai pannelli fino al quadro; un cavo **DT-240** dal quadro all'inverter.

**Il click destro a mano vuota lo isola**: senti scattare il sezionatore sotto carico e la potenza si
ferma lì. È il modo di lavorare a valle in sicurezza. Riclicca per richiudere.

**Pannello:** quante vie sono occupate su quante, quanti ampere restano liberi nel rame, tensione e
corrente di sbarra, i metri di dorsale e la perdita. La riga di stato dice, in ordine di importanza:
`isolato` → `nessun inverter` → `vuoto` → il motivo di un rifiuto → `chiuso`.

**Perché rifiuta una stringa:** troppa corrente per il rame, troppa tensione per il sistema, vie finite.
Il pannello lo scrive.

### 4.3 Gli inverter (Volterra)

| Prodotto | Potenza AC | Stringhe | Tipo |
|---|---|---|---|
| **VX-10K** | 10 kW | 2–4 | di stringa, per un tetto |
| **VX-110K** | 110 kW | 9–18 | di stringa, commerciale |
| **VX-350K** | 352 kW | 16–32 | di stringa grande, con sezione DC fusibilata |
| **VC-2500K** | 2500 kW | fino a 288 | centrale, va alimentato da quadri |

**Collegamento in ingresso:** cavo **SC-6** dai pannelli (per i tre di stringa) oppure **DT-240** dai
quadri (per il centrale, e va bene anche per i grandi).

**Collegamento in uscita:** una campata di conduttore **MT** dall'isolatore sul tetto dell'inverter fino
a un trasformatore macchina, a una cabina, o a un altro inverter.

**Pannello:** potenza attiva, apparente, reattiva e fattore di potenza; tensione e corrente per fase;
tensione e corrente DC; rendimento; rapporto DC/AC; temperatura di armadio, dissipatore e aria interna;
velocità delle ventole; resistenza di isolamento (crolla quando piove: è normale); il *performance ratio*.

**Controlli:** il pulsante di marcia/arresto, il pulsante del modo redstone (**disabilitato** / **alto**
= gira solo con segnale / **basso** = gira solo senza segnale), il cursore del **limite di potenza** e
quello del **fattore di potenza**.

**Stati:** `clipping` quando i pannelli danno più di quanto l'inverter può convertire — non è un guasto, è
un impianto dimensionato bene. `derating` quando è troppo caldo e si autolimita. `fermo dal giocatore`,
`fermo dal computer`, `fermo dal redstone`: tre motivi diversi, scritti separatamente.

---

## 5. L'eolico, componente per componente

### 5.1 Le turbine (Cube)

| Prodotto | Potenza | Rotore | Vento di avvio | Vento di arresto | Torre |
|---|---|---|---|---|---|
| **SW-10** | 10 kW | 7 m | 3,0 m/s | 25 m/s | 2–4 blocchi |
| **C52-0.85** | 850 kW | 52 m | 4,0 m/s | 25 m/s | 4–7 |
| **C80-2.0** | 2000 kW | 80 m | 4,0 m/s | 25 m/s | 6–10 |
| **C90-3.0** | 3000 kW | 90 m | 3,5 m/s | 25 m/s | 8–11 |
| **C112-3.0** | 3000 kW | 112 m | 3,0 m/s | 25 m/s | 8–12 |
| **C130-4.0** | 4000 kW | 130 m | 3,0 m/s | 25 m/s | 9–13 |

**Come si monta:** impila i blocchi **Turbine Tower** fino all'altezza che vuoi, poi posa la turbina
sopra l'ultimo. Nient'altro sta in piedi sopra i 16 blocchi.

**Se sbagli altezza te lo dice:** *"… is built for … to … blocks of tower. This one stands …"*. E se la
torre è troppo alta per la macchina che le hai messo sopra, **l'acciaio cede**: la torre crolla.

**Collegamento:** una campata di conduttore **MT** dall'isolatore della navicella a un trasformatore
macchina, a una cabina, o a un'altra turbina (le turbine si mettono in fila così, come un vero parco).

**Pannello:** velocità del vento e turbolenza, giri del rotore, angolo di imbardata e di passo, coppia,
potenza istantanea e prodotta, temperature, e i tre cursori di limite.

**Stati nel mondo:** il rotore gira alla velocità che gli tocca, la navicella si orienta al vento, le pale
vanno a passo di bandiera quando è ferma. Sotto il vento di avvio non gira. Sopra il vento di arresto si
ferma e si mette in bandiera. Con vento forte e turbolenza alta la turbina mette **disturbi** in rete:
sbalzi e, occasionalmente, uno scatto di protezione. Si propagano lungo le campate e si attenuano con la
distanza.

---

## 6. La rete, componente per componente

### 6.1 Trasformatore macchina (Machine Transformer)

Il trasformatore che ogni generatore ha ai piedi. Tre isolatori passanti.
**Sotto** (lato macchina) prende una campata MT da una turbina o da un inverter; **sopra** manda MT verso
la cabina o verso un altro trasformatore. Click destro a mano vuota: ti scrive rapporto, potenza che ci
passa e perdite.

### 6.2 Trasformatore da sottostazione (Substation Transformer)

Il grande: sei isolatori, vasca su bacino di contenimento, radiatori, conservatore, commutatore sotto
carico. Occupa diverse celle.
I **tre isolatori bassi** vogliono conduttore **MT**, i **tre alti** vogliono conduttore **AT**. Da qui
parte la linea di trasmissione, e da qui **solo** verso tralicci o verso un tratto posato.

### 6.3 Sezionatore (Disconnector) e interruttore (Circuit Breaker), 24 kV

Si mettono **in mezzo** a una linea MT: tre isolatori sul lato linea, tre sul lato carico. Tira una
campata da monte ai primi tre e da lì ai secondi tre verso valle.

* **Click destro a mano vuota** lo apre o lo chiude.
* Il **sezionatore** rifiuta di aprirsi se c'è carico: *"There is load on it. A disconnector cannot break
  current — open the breaker first."* È il comportamento vero di un sezionatore.
* L'**interruttore** apre sotto carico, e **scatta da solo** sopra i 26 MW.
* Quando è aperto, la potenza si ferma: il lato linea e il lato carico diventano due nodi separati.
* **Come vedi lo stato:** il sezionatore mostra la lama alzata, la gola d'aria si vede da fuori.
  L'interruttore ha i contatti nel vuoto e non ha niente che si muova a vista: te lo dice con una
  **bandierina** che cambia posizione.

### 6.4 Cabina elettrica (Electric Cabin)

Il nodo di raccolta: un isolatore d'ingresso, uno d'uscita, tre scaricatori sul tetto. Prende MT da
turbine, inverter e trasformatori macchina, e la manda a pali, tralicci, trasformatori da sottostazione o
tratti posati. Accetta tutto tranne l'alta tensione.

### 6.5 Palo (Utility Pole)

Otto isolatori: due sbracci. Porta bundle in bassa tensione o conduttore MT — mai alta tensione.
Alimenta altri pali, kiosk e tratti posati.

**Click destro a mano vuota**: si apre la schermata degli **sbracci**, con cui sposti il palo di frazioni
di blocco in X, Y e Z e lo inclini in imbardata e in beccheggio. Serve per far seguire a una linea una
strada che non è allineata alla griglia. I fili che ci sono già si spostano col palo.

### 6.6 Kiosk (Power Box)

La fine della linea: prende un bundle in **bassa tensione** da un palo e converte in **FE** (Forge
Energy) per alimentare macchine di altri mod. Contiene 100 000 FE e ne trasferisce fino a 2 000 per tick.
Non alimenta nessun'altra macchina di questo mod.

### 6.7 Tralicci (Suspension / Tension / Terminal), 400 kV

Sei isolatori: due sbracci da tre fasi. Solo conduttore **AT**.

| Traliccio | A cosa serve |
|---|---|
| **Suspension** | tiene su la linea. Nove tralicci su dieci sono questo. |
| **Tension** | assorbe la differenza di tiro fra i due lati: dove la linea cambia direzione. |
| **Terminal** | assorbe tutto il tiro della linea e ha i tiranti per farlo: inizio e fine linea. |

Occupano un'impronta di quasi quattro blocchi e molte celle intorno. La cella sotto il centro
dell'impronta **non** è solida: sotto un traliccio ci si passa.

### 6.8 Tratti posati (Bundle Run / Medium-Voltage Run / Transmission Run)

Gli stessi tre conduttori, posati a terra come blocchi. Si collegano solo a un tratto della stessa
sezione, e alle macchine elencate al §3. Utile per attraversare senza pali, e per entrare in una
sottostazione.

---

## 7. Misura e controllo

### 7.1 Palo meteo (Meteorological Mast)

Sette strumenti su un solo bus: irraggiamento globale, diffuso e sul piano dei moduli, albedo,
temperatura dell'aria e di modulo, vento e direzione, neve.

**Cosa fa davvero:** se c'è una fila di pannelli entro **12 blocchi**, il palo la prende come riferimento
e misura sul **suo** piano — è così che si misura un impianto vero. Senza nessuna fila in raggio, il
pannello lo dice: *"No array in range: the plane and module readings are the mast's own"*, e le due letture
diventano quelle del palo stesso.

**Pannello:** le due colonne di strumenti, la frazione di diffuso, l'indice di serenità, l'albedo,
elevazione e azimut del sole, e in fondo la riga che dice quali strumenti sono in servizio.

### 7.2 Quadro di controllo impianto (Plant Controller)

Il quadro da cui si comanda tutto l'impianto: un armadio in lamiera su zoccolo, una porta con il pannello
HMI, tre lampade **RUN / REMOTE / ALARM** e il fungo di emergenza; dietro la feritoia sotto c'è la guida
DIN con lo switch Ethernet industriale, due gateway seriali, il controllore e il suo alimentatore a 24 V.
Sul tetto l'antenna del collegamento radio, sotto la piastra pressacavi per la fibra e i cavi di misura.

**Cosa fa:** trova ogni turbina e ogni inverter entro **64 blocchi** e li tiene tutti a un unico
riferimento di potenza, **ripartito in proporzione alla targa** di ciascuno — che è esattamente il modo in
cui lavora un regolatore d'impianto vero. Non serve nessun cavo: il collegamento è la radio sull'antenna.

**Tre modi**, col pulsante in fondo al pannello:
* **watching only** — non comanda niente, misura e riporta. Ogni macchina tiene il suo limite.
* **holding a setpoint** — tiene l'impianto al valore del cursore. Il cursore è in percentuale della
  potenza installata trovata in raggio.
* **following redstone** — il riferimento segue il segnale redstone più forte sul quadro: zero a segnale
  nullo, tutto l'impianto a quindici.

**Pannello:** la potenza dell'impianto sulla potenza installata, su una barra, con il riferimento segnato
sopra da una lancetta; quante unità sono in raggio, quante stanno girando, quante sono trattenute; il
riferimento in kW; e il valore che un comparatore legge.

**Un comparatore appoggiato al quadro legge quanto è carico l'impianto**, da 0 a 15. Con quello puoi
pilotare un circuito redstone qualsiasi con la produzione di tutto il campo.

**Se togli il quadro, ogni macchina si riprende il limite che aveva.** Vale anche quando spegni il modo o
quando una macchina esce dal raggio: non resta mai niente trattenuto senza che ci sia il quadro a dirlo.

---

## 8. I colori e gli stati, uguali in tutti i pannelli

| Colore | Significato |
|---|---|
| **verde** | a targa, o niente da segnalare |
| **ambra** | qualcuno o qualcosa la sta trattenendo di proposito |
| **rosso** | non produce, e non per scelta |
| **blu** | funziona, sotto la targa |
| **grigio** | quello che avrebbe potuto fare: dietro la barra, così una perdita si legge come un vuoto |

Sono gli stessi tre colori delle lampade sulla porta del quadro di controllo, con gli stessi tre
significati.

---

## 9. Come si costruisce: la lista della spesa

Tutto si fa al banco da lavoro, in tre livelli.

**Materia prima:** piastra d'acciaio (ferro + carbone), profilato (da due piastre), sbarra di rame
(3 rame), matassa (3 sbarre + pepita), resina (favo + slime), lingotto e wafer di silicio (dalla sabbia
fusa), vetro temperato.

**Componenti:** cella solare, diodo di bypass, connettore DC, scatola di giunzione, modulo di potenza,
banco di condensatori, nucleo magnetico, scheda di controllo, cuscinetto, ingranaggi, fusibile gPV,
sezionatore in carico, testa di sensore.

**Assiemi:** laminato PV, armadio, sezione DC, ponte inverter, rastrelliera, tubo di torsione, rotismo,
gruppo generatore, riduttore, pala (e pala lunga).

**Macchine:** ogni macchina si compone dagli assiemi. Per esempio:
* **FT-430** = 3 laminati + 2 piastre + profilato + rastrelliera + 2 connettori
* **HX-700** = 3 laminati + 2 tubi di torsione + un motoriduttore + scheda + 2 connettori
* **VX-350K** = armadio + 3 ponti + 2 sezioni DC + 2 nuclei + scheda
* **CB-16** = armadio + sezionatore in carico + 3 fusibili + 2 sbarre
* **C130-4.0** = armadio + scheda + motoriduttore + 3 gruppi generatore + 3 pale lunghe
* **Machine Transformer** = 3 isolatori + 2 matasse + 3 piastre + nucleo
* **Substation Transformer** = 3 isolatori + 2 piastre + sezionatore + 2 nuclei + tubo di torsione
* **Plant Controller** = armadio + 2 schede di controllo + sezionatore + 2 piastre + 2 profilati + rame
* **Meteorological Mast** = schermo + scheda + 3 teste di sensore + 2 profilati

Le bobine di conduttore e i cavi DC si fanno a mazzette: 8 bundle, 6 MT, 4 AT, 6 cavi solari, 4 dorsali
per ricetta.

**Attenzione all'attrezzo:** ogni macchina va rotta col piccone. Le macchine di acciaio pesante (pali,
cabine, kiosk, torri, turbine, tralicci, trasformatori, sezionatori) vogliono almeno il **piccone di
ferro**; pannelli, inverter, quadri, palo meteo e quadro di controllo vengono via col piccone di pietra.

---

## 10. Simulazione di un impianto completo

Un impianto ibrido: **circa 1,4 MW di fotovoltaico** e **due turbine da 3 MW**, che escono su una linea a
400 kV, con un kiosk in fondo che alimenta una casa.

Le coordinate sono relative: prendi un pianoro, chiama X l'asse est-ovest e Z l'asse nord-sud.

### Passo 1 — Il campo solare

Posa **quattro file di FT-430** in linea lungo X, con **due blocchi di distanza** fra una fila e l'altra
lungo Z, così l'ombra di una fila non finisce su quella dietro (il pannello di ogni fila ti dice, nella
riga delle perdite, quanto si stanno ombreggiando: se `rows` non è a zero, allarga).

Poi altre quattro file identiche accanto. In tutto otto file.

```
 Z+
  ▓▓▓▓▓▓▓▓   fila 1     ← cavo SC-6 che corre lungo il bordo sud
  ▓▓▓▓▓▓▓▓   fila 2
  ▓▓▓▓▓▓▓▓   fila 3
  ▓▓▓▓▓▓▓▓   fila 4
                        ▄▄  quadro CB-16
  ▓▓▓▓▓▓▓▓   fila 5
  ▓▓▓▓▓▓▓▓   fila 6
  ▓▓▓▓▓▓▓▓   fila 7
  ▓▓▓▓▓▓▓▓   fila 8
 X+ ─────────────────────────►
```

### Passo 2 — Le stringhe

Posa **cavo SC-6** in modo che tocchi ogni fila e converga verso un punto in mezzo al campo. Non serve un
cavo per fila: basta che ci sia rame continuo dalla fila al quadro. Le curve, gli incroci e le scatole di
giunzione se le disegna il cavo da sé.

Metti lì il **quadro CB-16**. Apri il suo pannello con la chiave: deve dire quante vie sono occupate. Se
dice `vuoto`, il rame non arriva: guarda dove si interrompe.

### Passo 3 — La dorsale e l'inverter

Dal quadro, posa **cavo DT-240** fino al punto dove metterai l'inverter, a una ventina di blocchi. Metti
un **VX-350K**.

Apri il pannello dell'inverter: deve elencare le stringhe collegate e i quadri collegati. La riga
`DC/AC` ti dice come l'hai dimensionato: sopra 1,2 vedrai `clipping` nelle ore centrali, ed è giusto così.

### Passo 4 — Le turbine

Metti due **C90-3.0**. Per ciascuna: impila i **Turbine Tower** e conta — la C90 vuole da **8 a 11**
blocchi di torre. Nove va bene. Poi posa la turbina sopra l'ultimo blocco. Tienile lontane fra loro
almeno un paio di diametri di rotore, o quella a valle sta nella scia dell'altra.

Se sbagli il conto, il messaggio te lo dice prima; se la torre è troppo alta, cede.

### Passo 5 — I trasformatori macchina

Un **Machine Transformer** ai piedi di ogni turbina, e uno accanto all'inverter. Tre in tutto.

Prendi la bobina di **Aster 228** (media tensione):
* campata dall'**isolatore della navicella** della turbina 1 → isolatore basso del suo trasformatore;
* la stessa cosa per la turbina 2;
* campata dall'**isolatore sul tetto dell'inverter** → isolatore basso del suo trasformatore.

Ogni campata: click destro sul primo isolatore, click destro sul secondo. Se ti dice che la classe è
sbagliata, hai in mano il conduttore sbagliato.

### Passo 6 — La raccolta in cabina

Metti una **Electric Cabin** al centro dei tre. Con la stessa bobina MT:
* trasformatore turbina 1 (isolatore alto) → **ingresso** cabina;
* trasformatore turbina 2 (isolatore alto) → **ingresso** cabina;
* trasformatore inverter (isolatore alto) → **ingresso** cabina.

### Passo 7 — Sezionatore e interruttore

Fra la cabina e la sottostazione mettine due in fila, un **Circuit Breaker** e un **Disconnector**. Da
quale lato stanno non cambia niente: quello che conta è che **l'interruttore è quello che apre sotto
carico**, e il sezionatore è quello che poi rende visibile la gola d'aria.

Con la bobina MT:
* **uscita** cabina → uno dei tre isolatori *lato linea* dell'interruttore;
* isolatore *lato carico* dell'interruttore → isolatore *lato linea* del sezionatore;
* isolatore *lato carico* del sezionatore → isolatore basso del trasformatore da sottostazione.

Prova, a mano vuota: apri l'interruttore — si apre. Chiudilo. Prova ad aprire il **sezionatore** mentre
l'impianto produce: rifiuta, e ti spiega perché. Apri prima l'interruttore, poi il sezionatore: adesso si
apre. Questa è la manovra vera, e nel mod funziona così.

### Passo 8 — La sottostazione e la linea a 400 kV

Metti il **Substation Transformer**. Lascia spazio: occupa parecchie celle.

Poi la linea. Prendi la bobina di **Curlew ACSR quadruplo**:
* metti un **Terminal Tower** vicino alla sottostazione: è il traliccio che regge tutto il tiro;
* campata dai **tre isolatori alti** del trasformatore → tre isolatori del traliccio terminale;
* poi una fila di **Suspension Tower** ogni 100–150 blocchi (il massimo è 160), campata da tre isolatori
  ai tre corrispondenti del traliccio successivo — fase per fase, non incrociarle;
* dove la linea gira, metti un **Tension Tower** invece di un suspension;
* all'altro capo, un altro **Terminal Tower**.

### Passo 9 — La discesa in bassa tensione

Al capo della linea: dal traliccio terminale, campata **MT** a un **palo**. Poi da palo a palo con MT
fino dove ti serve, e per l'ultimo tratto passa al **bundle in bassa tensione** fino a un **kiosk**.

Il kiosk dà FE: attaccaci il macchinario di un altro mod.

Se una linea di pali deve seguire una strada storta, apri lo **sbraccio** del palo a mano vuota e
spostalo: i fili già tirati lo seguono.

### Passo 10 — Il palo meteo

Metti un **Meteorological Mast** entro **12 blocchi** da una fila di pannelli. Apri il suo pannello:
la riga in fondo deve dire che gli strumenti del piano sono sulla fila. Se dice *"No array in range"*,
avvicinalo.

Da qui leggi se una giornata brutta è nuvola, sporco o neve: confronta l'irraggiamento del palo con la
potenza che l'inverter fa davvero.

### Passo 11 — Il quadro di controllo

Metti il **Plant Controller** entro **64 blocchi** dalle due turbine e dall'inverter. Apri il pannello:
deve dire **3 unità** e la somma delle targhe, cioè circa **6,35 MW**.

Ora prova le tre cose che sa fare:

1. **watching only** — non comanda: vedi solo cosa fa l'impianto. La barra è blu.
2. **holding a setpoint** — porta il cursore al 50 %. La barra diventa ambra e la lancetta si mette a
   metà. Guarda i pannelli delle turbine: ognuna ha adesso un limite pari a metà della *sua* targa. Il
   riferimento è ripartito in proporzione, non diviso in parti uguali.
3. **following redstone** — attacca una leva al quadro, e poi un comparatore con una ripetizione: con la
   leva tirata l'impianto va al massimo, con un segnale debole si tiene basso.

E metti un **comparatore** appoggiato al quadro: il suo segnale è quanto è carico tutto il campo, da 0 a
15. Con quello puoi accendere una lampada quando l'impianto passa il 60 %, o fermare un forno di notte.

### Passo 12 — La prova finale

Con la chiave inglese, percorri la catena e leggi ogni macchina, in ordine:

```
fila FT-430 ──SC-6──► CB-16 ──DT-240──► VX-350K ──MT──► TX macchina ──┐
                                                                      │
     C90-3.0 ──MT──► TX macchina ───────────────────────────────────►─┤
     C90-3.0 ──MT──► TX macchina ───────────────────────────────────►─┤
                                                                      ▼
                                                                   CABINA
                                                                      │ MT
                                                                      ▼
                                                              INTERRUTTORE
                                                                      │ MT
                                                                      ▼
                                                               SEZIONATORE
                                                                      │ MT
                                                                      ▼
                                                          TX SOTTOSTAZIONE
                                                                      │ AT 400 kV
                                                                      ▼
                                          TERMINAL ─ SUSPENSION ─ … ─ TERMINAL
                                                                      │ MT
                                                                      ▼
                                                            PALO ─ PALO ─ PALO
                                                                      │ BT
                                                                      ▼
                                                                   KIOSK ──► FE
```

A ogni passo la potenza letta deve essere un po' meno di quella a monte: quello che manca è finito nelle
perdite del rame e in quelle dei trasformatori. Se in un punto la lettura è **zero** mentre a monte non lo
è, il collegamento è rotto lì: guarda se una campata manca, se un sezionatore è aperto, o se hai messo la
classe di conduttore sbagliata.

---

## 11. Gli errori che si fanno tutti, e come si vedono

| Sintomo | Causa quasi certa |
|---|---|
| l'inverter dice `nessuna stringa` | hai usato la dorsale DT-240 dove va il cavo SC-6, o il rame si interrompe |
| il quadro dice `vuoto` | il cavo SC-6 non arriva davvero alle file |
| il quadro rifiuta stringhe | troppa corrente per il rame, o vie finite: leggi il motivo nel pannello |
| non riesco a tirare una campata | conduttore della classe sbagliata, o troppo lontano, o bobina insufficiente |
| la potenza si ferma a metà catena | un sezionatore è aperto — la gola d'aria si vede da fuori |
| la torre è crollata | più blocchi di torre di quanti quella turbina regge |
| la turbina non gira | vento sotto l'avvio, o sopra l'arresto, o fermata da redstone o dal quadro |
| l'inverter dice `clipping` a mezzogiorno | non è un guasto: hai più pannelli che inverter, ed è normale |
| l'inverter dice `derating` | è caldo e si sta autolimitando |
| tutto l'impianto produce a metà senza motivo | il quadro di controllo è in `holding a setpoint`: guarda il cursore |
| una macchina resta limitata dopo aver tolto il quadro | non può succedere: il quadro restituisce i limiti quando lo rompi |
| la fila di pannelli si è messa piatta | è andata in bandiera per vento o neve; il pannello dice perché |
| il pannello di una macchina non si apre | avevi qualcosa in mano: usa la chiave inglese, o la mano vuota |
