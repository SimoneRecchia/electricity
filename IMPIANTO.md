# Il sito — due parchi solari, un parco eolico, una sottostazione 400/33 kV

Un sito completo, costruito e letto macchina per macchina su un server prima di essere scritto qui:
**12,54 MW** in tutto, quattro turbine, tre inverter, quattro campi, una sottostazione recintata con
strada, ghiaia, edificio di controllo, **servizi ausiliari** e **35 cartelli** che dicono cos'è ogni cosa e
a che tensione.

Lo genera e lo verifica [tools/gen_example_site.py](tools/gen_example_site.py).
Per il solo impianto solare minimo c'è [IMPIANTO-SOLARE.md](IMPIANTO-SOLARE.md).

## Come entrarci

Il mondo `run/impianto` è un **superpiatto Redstone Ready** (deserto, superficie a y=55) con il sito già in
piedi, in creativa, trucchi attivi.

```bash
./gradlew runServer
```

e ti colleghi a `localhost`. Compari a **(0, 56, 0)**: davanti a te la strada che attraversa tutto il sito,
i parchi solari a sud, la sottostazione a est, il parco eolico a nord.

Per costruirlo altrove: `python3 tools/gen_example_site.py`, poi il datapack
(`build/site/datapack`, già dentro il mondo) con `/function electricity:sito`, oppure
`python3 tools/rcon.py -f build/site/site.txt` su un server.

**Le 29 campate restano da tirare a mano** — una campata è salvata contro due isolatori, non contro un
blocco. L'elenco con le coordinate è al §5. Ti servono **circa 20 bobine MT**, **6 AT** e **2 BT**.

---

## 1. Cosa c'è, e quanto fa

| | Potenza | Letto in gioco |
|---|---|---|
| **Parco solare A** — 36 inseguitori biassiali AE-440 | 205,9 kW | **170,4 kW** |
| **Parco solare B** — 32 fissi obliqui TR-580 | 334,1 kW | **297,7 kW** |
| **Parco eolico** — 4 × Cube C90-3.0 | 12,00 MW | **2,74 MW** a 8,4 m/s |
| **Totale** | **12,54 MW** | |

E poi: 2 quadri di stringa, 3 inverter, 7 trasformatori macchina, 2 trasformatori da sottostazione,
1 interruttore, 1 sezionatore, 1 sbarra MT, 5 tralicci, 2 pali, 2 quadri BT, 1 palo meteo,
2 quadri di controllo, 36 blocchi di torre, 57 blocchi di cavo, 2 269 blocchi fra ghiaia, strada, plinti,
recinzione ed edificio.

---

## 2. La mappa

```
                                    ┌── PARCO EOLICO ──────────────────────────┐
   z=-110                           │  [WTG-02]                    [WTG-04]    │
                                    │     │ 50 blocchi                  │      │
   z=-60                            │  [WTG-01]────────────────────[WTG-03]    │
                                    │     │        [CONTROLLO EOLICO]          │
                                    └─────│────────────────────────────────────┘
                                          │ collettore MT 33 kV
   z=-9    ╔═══════════════════════════════════════════════════════════════╗
           ║  SOTTOSTAZIONE 400/33 kV        [TX AUX]══[MT]══[PALO]══[QBT]  ║  ← servizi ausiliari
   z=-4    ║  [SBARRA MT]──[INTERRUTTORE]──[SEZIONATORE]                    ║
   z=0   ──╫──[cancello]                        [TR1 400/33]──[STALLO LINEA]╫── 400 kV ──►
   z=6     ║  [EDIFICIO CONTROLLO][regolatore]                              ║
           ╚═══════════════════════════════════════════════════════════════╝
   z=8..13   PARCO SOLARE A — biassiali: due spine est-ovest, una fila sopra e una sotto
   z=18      dorsale DC ══► [VX-350K] ─► [TX macchina B]
   z=19      [CB-16]   [CB-32]
   z=20..27  PARCO SOLARE B — fissi: due spine nord-sud, file a est e a ovest
           x=6      x=16   x=21  x=24        x=46  x=52  x=56  x=62  x=72 ... x=152  x=160   x=178
```

La linea a 400 kV corre su **z=0**: stallo (72) → sostegni 2, 3, 4 (92, 112, 132) → sostegno 5 (152) →
sottostazione di arrivo (160) → tratto MT posato → palo (171) → cabina di consegna (178).

---

## 3. I quattro sottosistemi, uno per uno

### Parco solare A — inseguitori biassiali

Due campi identici, ognuno **18 × AE-440** su una **spina est-ovest** con nove file sopra e nove sotto,
dritti dentro un **VX-110K**: 18 stringhe contro 18 ingressi, 103 kW contro 110.

Perché la spina è est-ovest: **una fila su inseguitore prende il cavo a nord o a sud**, qualunque cosa
guardi. Il suo orientamento non conta: si gira lei verso il sole.

Niente quadro di stringa, e non è una semplificazione: **un inverter di stringa non ha i morsetti per la
dorsale**, quindi un quadro davanti a lui resterebbe pieno e scollegato. I quadri vogliono il VX-350K.

| | |
|---|---|
| Campo A1 | spina z=8, file z=7 e z=9, x=6..14 → VX-110K a (16, 56, 8) |
| Campo A2 | spina z=12, file z=11 e z=13 → VX-110K a (16, 56, 12) |
| Trasformatore | TX macchina a (20, 56, 10), 0,8 / 33 kV |

### Parco solare B — fissi obliqui

Due blocchi da 16 file **TR-580**, ognuno una **spina nord-sud** con otto file a est girate a ovest e otto
a ovest girate a est. I due quadri (**CB-16** e **CB-32**) stanno a nord del campo, sulla stessa dorsale
verso un **VX-350K**: 32 stringhe, che è esattamente quello che prende.

Perché est e ovest: **il sole di questo mod sta solo a est (mattina) o solo a ovest (pomeriggio)**, mai a
sud. Una fila fissa girata a nord o a sud sta a 90° dal sole tutto il giorno e rende meno di un tavolo
piatto. Metà da una parte e metà dall'altra appiattisce la curva della giornata. E **una fila fissa prende
il cavo sull'asse che guarda**, per questo la spina è nord-sud.

| | |
|---|---|
| Blocco B1 | spina x=8, file x=7 e x=9, z=20..27 → CB-16 a (8, 56, 19) |
| Blocco B2 | spina x=13, file x=12 e x=14 → CB-32 a (13, 56, 19) |
| Dorsale | DT-240 da z=18, x=8..20 → VX-350K a (21, 56, 18) |
| Trasformatore | TX macchina a (24, 56, 18), 0,8 / 33 kV |

### Parco eolico

Quattro **Cube C90-3.0** su nove blocchi di torre, distanziate **50 blocchi** — cinque diametri di rotore,
sotto i quali quella a valle sta nella scia di quella a monte.

Ognuna ha il suo **trasformatore macchina** al piede, su plinto, e le quattro sono in **catena su un unico
collettore MT**, come un feeder vero: WTG-02 → WTG-01, WTG-04 → WTG-03 → WTG-01 → sbarra MT.

| Macchina | Posizione | Trasformatore |
|---|---|---|
| WTG-01 | (54, 65, −60) | (57, 56, −57) |
| WTG-02 | (54, 65, −110) | (57, 56, −107) |
| WTG-03 | (104, 65, −60) | (107, 56, −57) |
| WTG-04 | (104, 65, −110) | (107, 56, −107) |

Il parco ha il **suo** quadro di controllo, a (79, 56, −85). Non è un vezzo: il quadro arriva a 64 blocchi
e le macchine sono a 50 l'una dall'altra, quindi uno solo non copre sito e parco. E **due quadri che
arrivano alla stessa macchina se la contendono** — il generatore adesso lo rifiuta come errore di layout,
perché è successo davvero mentre lo costruivo.

### Sottostazione 400/33 kV

Il recinto di rete con il cancello sulla strada, la ghiaia, i plinti, l'edificio di controllo con la porta
e le finestre, e le sezioni nell'ordine in cui la corrente le attraversa:

| Sezione | Dove | Cartello |
|---|---|---|
| **Sbarra MT 33 kV** (cabina) | (46, 56, −4) | SBARRA MT / 33 kV / quadro di raccolta |
| **Interruttore di sbarra** | (52, 56, −4) | apre in carico |
| **Sezionatore** | (56, 56, −4) | non apre in carico |
| **TR1 — trasformatore AT/MT** | (62, 56, 0) | 400 / 33 kV, stallo TR1 |
| **Stallo linea** (traliccio terminale) | (72, 56, 0) | LINEA 1, partenza |
| **Edificio controllo + regolatore** | (46..49, 56, 0..4) e (50, 56, 6) | |

---

## 4. I servizi ausiliari

Una sottostazione alimenta le proprie ventole, le luci e i quadri di comando, e lo fa dal **suo**
trasformatorino attaccato alla sbarra MT. Qui c'è tutta la catena:

```
SBARRA MT 33 kV ──MT──► [TX SERVIZI AUSILIARI 33/0,4 kV] (50,56,-9)
                            │ MT
                            ▼
                    [tratto MT posato] (53..56, 56, -9)
                            │ MT
                            ▼
                        [PALO] (60,56,-9)
                            │ BT bundle 400 V
                            ▼
                  [QUADRO BT AUSILIARI] (66,56,-9) ──► FE
```

Il tratto posato e il palo non sono decorazione: un trasformatore da sottostazione manda media tensione
**solo** a un traliccio o a un tratto posato, e solo un palo scende poi in bassa tensione. È la stessa
catena, in miniatura, che porta la corrente fuori dal sito.

---

## 5. Le 29 campate, in ordine

Bobina in mano, click destro sul primo isolatore, click destro sul secondo.

**Raccolta solare (MT)**
1. inverter A1 (16,56,8) → TX solare A (20,56,10) — 4
2. inverter A2 (16,56,12) → TX solare A (20,56,10) — 4
3. inverter VX-350K (21,56,18) → TX solare B (24,56,18) — 3
4. TX solare A → sbarra MT (46,56,−4) — 30
5. TX solare B → sbarra MT — 31

**Raccolta eolica (MT)**
6–9. ogni navicella (y=65) → il suo TX macchina — 10 cadauna
10. WTG-02 TX → WTG-01 TX — 50
11. WTG-04 TX → WTG-03 TX — 50
12. WTG-03 TX → WTG-01 TX — 50
13. WTG-01 TX → sbarra MT — 54

**Sottostazione (MT, poi AT)**
14. sbarra MT (uscita) → interruttore (52,56,−4) — 6
15. interruttore → sezionatore (56,56,−4) — 4
16. sezionatore → TR1 isolatore basso (62,56,0) — 7
17. **AT** TR1 isolatore alto → stallo linea (72,56,0) — 10

**Servizi ausiliari**
18. sbarra MT (uscita) → TX ausiliari (50,56,−9) — 6
19. TX ausiliari → tratto MT posato (53,56,−9) — 3
20. tratto MT posato (56,56,−9) → palo (60,56,−9) — 4
21. **BT** palo → quadro BT ausiliari (66,56,−9) — 6

**Linea 400 kV**
22–25. **AT** sostegno 1 → 2 → 3 → 4 → 5 (72 → 92 → 112 → 132 → 152) — 20 cadauna
26. **AT** sostegno 5 → TX arrivo isolatore alto (160,56,0) — 8

**Consegna**
27. TX arrivo isolatore basso → tratto MT posato (164,56,0) — 4
28. tratto MT posato (167,56,0) → palo di consegna (171,56,0) — 4
29. **BT** palo → cabina di consegna (178,56,0) — 7

Le tre fasi di ogni traliccio vanno tirate una per una fra gli isolatori corrispondenti: non incrociarle.

---

## 6. Le regole che hanno deciso la forma del sito

Nessuna è indovinabile, e ognuna è costata un tentativo sbagliato.

1. **Il sole sta solo a est o solo a ovest.** Una fila fissa va girata a est o a ovest; a nord o a sud
   rende meno di un tavolo piatto. Un inseguitore si gira da solo.
2. **Una fila prende il cavo su un asse solo:** fissa sull'asse che guarda, su inseguitore a nord/sud.
   Sbagliare asse dà un campo che sembra cablato e non porta niente.
3. **Un inverter ha un numero di stringhe e un tetto di potenza continua**, non solo dei kW. Oltre, accetta
   le file **più vicine** e lascia fuori le altre in silenzio.
4. **Un quadro di stringa vuole un inverter con i morsetti per la dorsale**, che gli inverter di stringa
   non hanno.
5. **Un traliccio a 400 kV si unisce solo a un altro traliccio o agli isolatori alti di un trasformatore da
   sottostazione.** Scendere da una linea vuol dire un secondo trasformatore, e la sua uscita MT raggiunge
   un palo solo passando per un tratto posato.
6. **Due quadri di controllo che arrivano alla stessa macchina se la contendono.** Uno per parco.
7. **Distanziare le file non serve** (l'ombra fra file è una proprietà del prodotto), ma **qualsiasi cosa
   alta a est o a ovest di una fila la ombreggia**: per questo tutti gli armadi stanno a nord dei campi.
8. **Il TR-530 (film sottile)** non c'è: fa tre stringhe da 999 V e una sola stringa fuori finestra ferma
   l'inverter intero. Vuole un cabinet 500–1500 V.

---

## 7. La messa in servizio

Chiave inglese in mano, in questo ordine. Le letture sono quelle misurate su questo sito a mezzogiorno.

| # | Macchina | Dove | Deve dire |
|---|---|---|---|
| 1 | una fila biassiale | (6, 56, 7) | `Wired`, l'angolo che insegue |
| 2 | VX-110K campo A1 | (16, 56, 8) | **18 stringhe**, ~**84 kW** |
| 3 | VX-110K campo A2 | (16, 56, 12) | **18 stringhe**, ~**86 kW** |
| 4 | CB-16 / CB-32 | (8, 56, 19) / (13, 56, 19) | **16 vie su 16** e **16 su 32** |
| 5 | VX-350K | (21, 56, 18) | **2 quadri, 32 stringhe**, ~**298 kW** |
| 6 | WTG-01…04 | y=65 | vento ~8,4 m/s, **0,6–0,9 MW** cadauna e in salita |
| 7 | TX macchina (7) | vari | rapporto, potenza, perdite (mano vuota) |
| 8 | sbarra MT | (46, 56, −4) | la somma di solare ed eolico |
| 9 | interruttore | (52, 56, −4) | si apre e si chiude a mano vuota |
| 10 | sezionatore | (56, 56, −4) | **rifiuta** sotto carico: apri prima l'interruttore |
| 11 | TR1 → stallo → sostegni → arrivo | z=0 | scende un po' a ogni passo: sono le perdite |
| 12 | quadro BT ausiliari | (66, 56, −9) | accumula FE |
| 13 | cabina di consegna | (178, 56, 0) | accumula FE |
| 14 | regolatore sottostazione | (50, 56, 6) | **3 unità**, **572 kW** |
| 15 | regolatore parco eolico | (79, 56, −85) | **4 unità**, **12 000 kW** |

Provati sul posto: il regolatore della sottostazione messo a **286 kW** ha dato ai tre inverter
**55 / 55 / 176 kW** — metà della targa di ciascuno — e non ha toccato la turbina vicina. Quello del parco
eolico messo a **6 MW** ha dato **1 500 kW a ognuna delle quattro**.

---

## 8. Cosa provare

* **Apri l'interruttore di sbarra**: tutto il sito si stacca dalla linea, e la lettura a valle va a zero.
* **Prova ad aprire il sezionatore prima**: rifiuta, e ti dice perché.
* **Isola il CB-32** a mano vuota: il VX-350K perde 16 stringhe.
* **Aspetta il tramonto:** le file girate a ovest tengono mentre quelle a est sono già a zero, e le
  biassiali seguono fino all'ultimo.
* **Comparatore su un quadro di controllo:** legge quanto è carico il parco, da 0 a 15.
* **Guarda le turbine imbardare** quando il vento gira: si allineano da sole, e finché non si allineano
  non producono.
