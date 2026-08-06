# Impianto solare da 533 kW — scheda di costruzione

Un impianto fotovoltaico completo, con coordinate esatte: quattro sottocampi, quattro tipi di pannello,
tre quadri di stringa, due inverter, due trasformatori macchina, interruttore, sezionatore, cabina,
sottostazione, linea a 400 kV su tralicci, sottostazione di arrivo, tratto MT posato a terra, pali,
kiosk, palo meteo e quadro di controllo.

Non è un esempio inventato: è generato e **verificato** da
[tools/gen_example_plant.py](tools/gen_example_plant.py), che prima di scrivere qualsiasi cosa controlla
che nessuna macchina invada le celle di un'altra, che ogni fila tocchi il cavo della sezione giusta, che
ogni rete in continua sia un pezzo solo e non ne tocchi un'altra, e che ogni campata stia dentro la
portata del suo conduttore.

## Costruirlo da solo

Prima genera, e questo non tocca il gioco: funziona sempre, anche senza Minecraft aperto.

```bash
python3 tools/gen_example_plant.py
```

Scrive **due** cose, perché ci sono due modi di avere un mondo.

### A) In un mondo qualsiasi, anche in singleplayer — il datapack

È il modo normale. `build/plant/datapack/` è un datapack con una sola funzione, scritta a **coordinate
relative**: costruisce l'impianto **partendo dal blocco su cui stai in piedi**.

1. copia la cartella `build/plant/datapack` dentro `saves/<il tuo mondo>/datapacks/`
   (su Mac: `~/Library/Application Support/minecraft/saves/…`; per il mondo del mod: `run/saves/…`);
2. entra nel mondo, con i **trucchi attivi** (o da operatore su un server);
3. mettiti in un posto pianeggiante e libero, guardando dove vuoi che vada il campo;
4. `/reload`
5. `/function electricity:plant`

L'impianto compare tutto in un colpo, quindi guardati intorno prima: occupa **da 3 blocchi a ovest di te
fino a 144 a est** (148 in tutto) e **17 blocchi verso sud**. Il campo solare sono i primi 20 blocchi a est;
il resto è la linea, che se non hai spazio puoi anche non costruire — l'impianto produce comunque, e la
lettura si fermerà alla cabina.

### B) Sul server di sviluppo — RCON

Serve solo se stai provando la mod con `./gradlew runServer`. Con il server **avviato**, da un altro
terminale:

```bash
python3 tools/rcon.py -f build/plant/plant.txt
```

Questo usa coordinate **assolute**, quindi decidile prima:

```bash
python3 tools/gen_example_plant.py --at 200 64 200
```

RCON è un protocollo da server: su un mondo aperto in singleplayer **non funziona** — lì usa il datapack.

### In entrambi i casi: le campate restano da fare

Le **campate** non si possono piazzare con un comando: una campata è salvata contro due isolatori, non
contro un blocco, e va tirata a mano con la bobina. Sono 16, elencate al §5 con le coordinate.

Ti serviranno **2 bobine di conduttore MT** (8 blocchi cadauna, 9 campate), **6 di AT** (16 blocchi
cadauna, 5 campate da 20) e **2 di BT**. In creativa prendile dalla scheda del mod.

---

## 1. Cosa fa, in numeri

| | |
|---|---|
| Moduli in campo | **533,0 kW** in **76 stringhe** |
| Inverter | **462,0 kW** in alternata (VX-350K + VX-110K) |
| Rapporto DC/AC | **1,15** — corretto: a mezzogiorno d'estate vedrai `clipping`, ed è come deve essere |
| Vie fusibilate | 64 (due CB-16 e un CB-32) |
| Blocchi di cavo | 64 |
| Campate da tirare | 16 |
| Comandi di costruzione | 135 |

Il conto dei moduli, blocco per blocco:

| Prodotto | Pezzi | Stringhe cadauno | kW cadauno | Totale |
|---|---|---|---|---|
| **TR-580** inclinazione fissa | 16 | 1 | 10,44 | 167,0 kW |
| **TR-530** inclinazione fissa (film sottile) | 10 | 3 | 9,54 | 95,4 kW |
| **HX-700** inseguitore a un asse | 16 | 1 | 9,10 | 145,6 kW |
| **FT-430** tavolo piatto | 6 | 2 | 18,92 | 113,5 kW |
| **AE-440** inseguitore a due assi | 2 | 1 | 5,72 | 11,4 kW |

---

## 2. Le due cose che decidono tutto il campo, e non si indovinano

**Il sole in questo mod sta solo a est o solo a ovest.** Tutta la mattina è a est, tutto il pomeriggio a
ovest, e non passa mai per sud. Quindi:

* una fila a **inclinazione fissa** va orientata **a est o a ovest**, e basta. Una fila orientata a nord o
  a sud sta a 90° dal sole tutto il giorno: la sua inclinazione non guadagna niente e rende meno di un
  tavolo piatto.
* **metà delle file a est e metà a ovest** è quello che appiattisce la curva della giornata — mattina da
  una metà, pomeriggio dall'altra. È come sono disposte tutte le file di questo impianto.
* una fila **su inseguitore ignora completamente il suo orientamento**: si gira lei verso il sole. Puoi
  posarla come vuoi.

**L'ombreggiamento fra file non dipende da quanto le distanzi.** È una proprietà del prodotto (il suo
rapporto di copertura del suolo), quindi le file possono stare attaccate. Quello che invece **ombreggia
davvero è qualsiasi cosa stia a est o a ovest di una fila**, perché è lì che sta il sole: un armadio, un
albero, un muro. Per questo su questo impianto **ogni armadio sta a nord del campo che serve**, e le
estremità est e ovest delle file sono libere.

---

## 3. La mappa

Coordinate relative all'origine che passi con `--at`. X cresce verso **est**, Z verso **sud**.
Tutto sta a quota Y+0, cioè appoggiato per terra.

```
        x=-3   x=0 ────────────── x=8   x=9    x=10..12  x=15   x=18   x=22  x=25      x=29
        ┌────┐
 z=0    │    │  ████████ 8 × TR-580 (facing=east)
 z=1    │    │  ──────── cavo SC-6 ────────────► [CB-16]══dorsale══╗
 z=2    │    │  ████████ 8 × TR-580 (facing=west)                  ║        [ctrl]
 z=3    │    │                                                     ║
 z=4    │mast│  █████    5 × TR-530 (facing=east)                  ║
 z=5    │    │  ──────── cavo SC-6 ────────────► [CB-32]══dorsale══╬══► [VX-350K] ─► [TX-A] ─► [INT] ─► [SEZ] ─┐
 z=6    │    │  █████    5 × TR-530 (facing=west)                  ║                                            │
 z=7    └────┘                                                     ║                                            │
 z=8            ████████ 8 × HX-700 (inseguitori)                  ║                                            ▼
 z=9            ──────── cavo SC-6 ────────────► [CB-16]══dorsale══╝                                        [CABINA]
 z=10           ████████ 8 × HX-700 (inseguitori)                                                               │
 z=11                                                                                                           │
 z=14           ███ 3 × FT-430 (east)   █ AE-440                                                                │
 z=15           ──────── cavo SC-6 ─────────────────────────────► [VX-110K] ─► [TX-B] ─────────────────────────►┘
 z=16           ███ 3 × FT-430 (west)   █ AE-440
```

E la linea, tutta sulla riga z=10:

```
 x=34          x=42          x=62          x=82          x=102         x=110        x=114..118   x=122   x=136   x=144
[TX SOTTOST.] [TERMINALE] ─ [SOSPENS. 1] ─ [SOSPENS. 2] ─ [TERMINALE] [TX ARRIVO] ══MT posato══ [PALO] ─ [PALO] ─ [KIOSK]
      │            └──────── 400 kV, campate da 20 blocchi ────────┘        │
      └── MT dalla cabina                                                   └── MT in uscita
```

---

## 4. L'elenco dei blocchi, con le coordinate

### Sottocampo 1 — 16 × TR-580 → CB-16 (167 kW, 16 stringhe: il quadro è pieno)

| Cosa | Dove | Stato |
|---|---|---|
| 8 × `pv_tilt_580` | (0..7, 0) | `facing=east` |
| cavo `dc_string_cable` | (0..8, 1) | la spina: le file sopra e sotto la toccano |
| 8 × `pv_tilt_580` | (0..7, 2) | `facing=west` |
| `pv_combiner_16` | (9, 1) | `facing=west` |

### Sottocampo 2 — 10 × TR-530 → CB-32 (95 kW, 30 stringhe su 32 vie)

| Cosa | Dove | Stato |
|---|---|---|
| 5 × `pv_tilt_530` | (0..4, 4) | `facing=east` |
| cavo `dc_string_cable` | (0..8, 5) | |
| 5 × `pv_tilt_530` | (0..4, 6) | `facing=west` |
| `pv_combiner_32` | (9, 5) | `facing=west` |

### Sottocampo 3 — 16 × HX-700 → CB-16 (146 kW, 16 stringhe)

| Cosa | Dove | Stato |
|---|---|---|
| 8 × `pv_track_700` | (0..7, 8) | orientamento indifferente: si girano da sole |
| cavo `dc_string_cable` | (0..8, 9) | |
| 8 × `pv_track_700` | (0..7, 10) | |
| `pv_combiner_16` | (9, 9) | `facing=west` |

### La dorsale e l'inverter centrale

| Cosa | Dove |
|---|---|
| `dc_trunk_cable` | (10..12, 1), (10..12, 5), (10..12, 9), (12, 1..9), (12..14, 5) |
| `inverter_350` (VX-350K) | (15, 5) `facing=west` |
| `tx_machine` (TX-A) | (18, 5) `facing=west` |

I tre quadri stanno tutti sulla stessa dorsale: è **una** rete in continua, ed è così che si alimenta un
inverter centrale. 408 kW di moduli su 352 kW di inverter.

### Sottocampo 4 — l'altra topologia: 6 × FT-430 + 2 × AE-440 → VX-110K, senza quadro

| Cosa | Dove | Stato |
|---|---|---|
| 3 × `pv_flat_430` | (0..2, 14) | `facing=east` |
| 1 × `pv_dual_440` | (4, 14) | |
| cavo `dc_string_cable` | (0..14, 15) | |
| 3 × `pv_flat_430` | (0..2, 16) | `facing=west` |
| 1 × `pv_dual_440` | (4, 16) | |
| `inverter_110` (VX-110K) | (15, 15) `facing=west` |
| `tx_machine` (TX-B) | (18, 15) `facing=west` |

Questa è una **seconda rete in continua**, separata dalla prima: le stringhe vanno direttamente
all'inverter, senza quadro, che è come si fa un tetto o un piccolo impianto a terra. 125 kW su 110 kW.

### La sezione di media tensione

| Cosa | Dove | Stato |
|---|---|---|
| `mv_breaker` (interruttore) | (22, 5) | `facing=west`, `open=false` |
| `mv_disconnector` (sezionatore) | (25, 5) | `facing=west`, `open=false` |
| `electric_cabin` (cabina) | (29, 10) | `facing=west` — occupa z 9..11 e 3 blocchi in altezza |

### La sottostazione e la linea

| Cosa | Dove | Note |
|---|---|---|
| `tx_substation` | (34, 10) | occupa 3×3 e 2 di altezza |
| `lattice_terminal` | (42, 10) | traliccio di partenza: regge tutto il tiro |
| `lattice_suspension` | (62, 10) | 5×5 di impronta, 12 di altezza |
| `lattice_suspension` | (82, 10) | |
| `lattice_terminal` | (102, 10) | traliccio di arrivo |

### La discesa

| Cosa | Dove | Note |
|---|---|---|
| `tx_substation` | (110, 10) | **la sottostazione di arrivo** — vedi il §6 |
| `mv_conductor_run` | (114..118, 10) | tratto MT posato a terra |
| `utility_pole` | (122, 10) | |
| `utility_pole` | (136, 10) | |
| `power_box` (kiosk) | (144, 10) | `mounted=false` — dà FE |

### Misura e controllo

| Cosa | Dove | Note |
|---|---|---|
| `met_station` (palo meteo) | (−3, 5) | **3,2 blocchi** dalla fila più vicina: dentro i 12 che gli servono |
| `plant_controller` | (18, 1) | **5,0** blocchi dal VX-350K e **14,3** dal VX-110K: dentro i 64 |

---

## 5. Le 16 campate, in ordine

Bobina in mano, click destro sul primo isolatore, click destro sul secondo.

| # | Conduttore | Da | A | Blocchi |
|---|---|---|---|---|
| 1 | MT | VX-350K, isolatore sul tetto (15, 5) | TX-A, isolatore basso (18, 5) | 3 |
| 2 | MT | VX-110K, isolatore sul tetto (15, 15) | TX-B, isolatore basso (18, 15) | 3 |
| 3 | MT | TX-A, isolatore alto (18, 5) | interruttore, lato linea (22, 5) | 4 |
| 4 | MT | TX-B, isolatore alto (18, 15) | interruttore, lato linea (22, 5) | 11 |
| 5 | MT | interruttore, lato carico (22, 5) | sezionatore, lato linea (25, 5) | 3 |
| 6 | MT | sezionatore, lato carico (25, 5) | cabina, ingresso (29, 10) | 6 |
| 7 | MT | cabina, uscita (29, 10) | TX sottostazione, isolatore basso (34, 10) | 5 |
| 8 | **AT** | TX sottostazione, isolatore alto (34, 10) | traliccio terminale (42, 10) | 8 |
| 9 | **AT** | traliccio terminale (42, 10) | sospensione 1 (62, 10) | 20 |
| 10 | **AT** | sospensione 1 (62, 10) | sospensione 2 (82, 10) | 20 |
| 11 | **AT** | sospensione 2 (82, 10) | traliccio terminale arrivo (102, 10) | 20 |
| 12 | **AT** | traliccio terminale arrivo (102, 10) | TX arrivo, isolatore alto (110, 10) | 8 |
| 13 | MT | TX arrivo, isolatore basso (110, 10) | tratto MT posato, primo blocco (114, 10) | 4 |
| 14 | MT | tratto MT posato, ultimo blocco (118, 10) | palo 1 (122, 10) | 4 |
| 15 | MT | palo 1 (122, 10) | palo 2 (136, 10) | 14 |
| 16 | **BT** | palo 2 (136, 10) | kiosk (144, 10) | 8 |

Le tre fasi dei tralicci vanno tirate una per una fra gli isolatori corrispondenti: non incrociarle.
Le campate 9, 10 e 11 sono da 20 blocchi contro un massimo di 160: c'è tutto il margine per allungare la
linea quanto vuoi.

Bobine che servono: **MT** 8 blocchi a bobina, **AT** 16, **BT** 4.

---

## 6. Perché serve una seconda sottostazione, e non un palo

È la cosa che sorprende, e non è un difetto: è la conseguenza delle classi di tensione.

Un **traliccio accetta solo conduttore di trasmissione**. Un **palo rifiuta la trasmissione**. Quindi
**non esiste nessun conduttore che si possa tirare fra un traliccio e un palo**: nessuna delle tre bobine
va bene per entrambe le estremità, e il gioco ti dirà *"Match the conductor to the voltage"* qualunque cosa
tu abbia in mano. Vale anche fra traliccio e cabina.

Per scendere da una linea a 400 kV serve quello che serve nella realtà: **una sottostazione di arrivo**.
Il traliccio terminale entra sui **tre isolatori alti** del trasformatore, e dai **tre bassi** esce media
tensione. E da un trasformatore da sottostazione la media tensione può andare solo verso un traliccio (che
la rifiuta) o verso un **tratto posato a terra** — quindi il tratto MT posato non è un vezzo, è l'unica
uscita. Da lì si arriva al palo, e dal palo al kiosk in bassa tensione.

In sintesi, la catena valida:

```
traliccio ──AT──► TX arrivo (isolatori alti)
                  TX arrivo (isolatori bassi) ──MT──► tratto posato ──MT──► palo ──BT──► kiosk
```

---

## 7. La messa in servizio, in ordine, con quello che devi leggere

Chiave inglese in mano, e leggi in questo ordine. Se un passo non torna, il guasto è lì e non a valle.

1. **Una fila qualsiasi.** Deve dire `Wired`. La riga delle condizioni deve dare temperatura di cella e
   angolo di incidenza plausibili. Se dice `Not wired`, il cavo SC-6 non la tocca.
2. **CB-16 nord.** Deve dire **16 vie su 16**, stato `chiuso`. Se dice `vuoto`, la spina non arriva.
3. **CB-32 centro.** **30 vie su 32**.
4. **CB-16 sud.** **16 su 16**.
5. **VX-350K.** Deve elencare **3 quadri collegati** e **62 stringhe**, e il rapporto DC/AC ≈ **1,16**.
   A mezzogiorno d'estate `clipping`: giusto così.
6. **VX-110K.** **14 stringhe**, nessun quadro, DC/AC ≈ **1,14**.
7. **TX-A e TX-B** (click destro a mano vuota): rapporto, potenza che passa e perdite.
8. **Interruttore.** Chiuso. Prova ad aprirlo: si apre, e la potenza a valle va a zero. Richiudilo.
9. **Sezionatore.** Con l'impianto in produzione prova ad aprirlo: **rifiuta**, e ti spiega perché. Apri
   prima l'interruttore, poi il sezionatore: adesso si apre e la gola d'aria si vede da fuori. Questa è la
   manovra vera, ed è l'unico punto dell'impianto dove l'ordine conta.
10. **Cabina, sottostazione, tralicci, TX di arrivo, pali:** la potenza letta deve scendere un po' a ogni
    passo — quello che manca è nelle perdite. Uno **zero** dove a monte non c'era è la campata che manca.
11. **Kiosk.** Deve accumulare FE.
12. **Palo meteo.** La riga in fondo deve dire che gli strumenti del piano sono sulla fila, non del palo.
13. **Quadro di controllo.** Deve dire **2 unità** e **462,0 kW** di potenza installata. Poi:
    * `watching only`: barra blu, non comanda niente;
    * `holding a setpoint` al 50 %: barra ambra, e i due inverter si trovano limitati a metà della **loro**
      targa — 176 kW e 55 kW, non 231 e 231. Il riferimento è ripartito in proporzione;
    * `following redstone`: attacca una leva e guarda l'impianto seguirla;
    * appoggia un **comparatore**: legge quanto è carico l'impianto, da 0 a 15.

---

## 8. Cosa provare, una volta che gira

* **Isola un quadro** (click destro a mano vuota sul CB-32): il VX-350K perde 30 stringhe e 95 kW, e il suo
  rapporto DC/AC scende. Richiudi.
* **Metti un blocco alto tre metri a est di una fila** e guarda `beam` scendere nella riga delle perdite
  al mattino, e tornare al pomeriggio. È l'ombra, tracciata per davvero.
* **Confronta il palo meteo con l'inverter** in una giornata di pioggia: l'irraggiamento crolla, la
  resistenza di isolamento dell'inverter crolla con lui, e la potenza segue.
* **Curtailment dal comparatore:** metti una leva e un comparatore sul quadro di controllo, e usa il
  segnale per accendere qualcosa quando l'impianto supera il 60 % di carico.
* **Nevica:** le file su inseguitore vanno in bandiera da sole e il pannello dice perché.
