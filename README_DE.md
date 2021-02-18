---
Beschreibung: >-
	Dieses Workflow-Plugin ermöglicht das automatische Schließen eines Schritts in mehreren Vorgängen.
---

# Automatisches Schließen von Schritten

## Einführung

Dieses Workflow-Plugin ermöglicht es, einen bestimmten Schritt in mehreren Vorgängen gleichzeitig zu schließen. Die dafür zur Verfügung stehenden Schritte werden vorher in einer Konfigurationsdatei festgelegt und stehen später auf der Web-Oberfläche zur Auswahl. Die Vorgänge, in denen der ausgewählte Schritt geschlossen werden soll, werden per Datei-Upload festgelegt. 

## Übersicht

<!---
Existiert das Projekt schon auf Github? Wenn es angelegt ist, den Pfad nochmal überprüfen
Passt die ältere unterstützte Goobi-Version?
-->
| Details | |
| :--- | :--- |
| Identifier          | intranda\_workflow\_closestep |
| Source code         | [https://github.com/intranda/goobi-plugin-workflow-closestep](https://github.com/intranda/goobi-plugin-workflow-closestep) |
| Lizenz              | GPL 2.0 oder neuer |
| Kompatibilität      | Goobi workflow 20.06 |
| Dokumentationsdatum | 15.02.2021 |

## Installation

Zur Installation müssen folgende Dateien installiert werden:

<!---
Wie entstehen diese Dateien? Braucht das Plugin auch die GUI-Datei?
-->

```bash
/opt/digiverso/goobi/plugins/workflow/plugin_intranda_workflow_closestep.jar
/opt/digiverso/goobi/plugins/GUI/plugin_intranda_workflow_closestep-GUI.jar
```

Um für das Plugin die schließbaren Schritte festzulegen, muss folgende Konfigurationsdatei angelegt und angepasst werden:

```bash
/opt/digiverso/goobi/config/plugin_intranda_workflow_closestep.xml
```

Der Inhalt dieser Konfigurationsdatei sieht wie folgt aus:

```markup
<config_plugin>
	<maximum_megabyte_per_file mb="5" />
	<!-- The status may be LOCKED, OPEN, INWORK, DONE, ERROR or DEACTIVATED -->
	<step_to_close name="Biografien prüfen">
		<condition stepname="Einspielen der Images" status="OPEN" />
		<condition stepname="Archivierung" status="DONE" />
	</step_to_close>
	<step_to_close name="Qualitätskontrolle">
		<condition stepname="Einspielen der Images" status="OPEN" />
		<condition stepname="Archivierung" status="DONE" />
	</step_to_close>
	<step_to_close name="Export in viewer">
		<condition stepname="Archivierung" status="DONE" />
		<condition stepname="Einspielen der Images" status="DONE" />
		<condition stepname="Bibliographische Aufnahme" status="DONE" />
	</step_to_close>
</config_plugin>
```

Zusätzlich kann die maximale Dateigröße (in Megabyte) für den Upload festgelegt werden. Diese ist aber wahrscheinlich in den seltensten Fällen relevant.

Für eine Nutzung dieses Plugins muss der Nutzer über die korrekte Rollenberechtigung verfügen.

<!---
Hier noch die richtige Rolle eintragen und die Bildschirmfotos machen
-->

![Ohne korrekte Berechtigung ist das Plugin nicht nutzbar](../.gitbook/assets/intranda_workflow_closestep1_de.png)

Bitte weisen Sie daher der Gruppe die Rolle `Plugin_Goobi_Closestep` zu.

![Korrekt zugewiesene Rolle f&#xFC;r die Nutzer](../.gitbook/assets/intranda_workflow_closestep2_de.png)

## Erläuterung der Konfigurationsoptionen

**Bitte in allen angegebenen Schrittnamen die korrekte Groß- und Kleinschreibung sowie den richtigen Gebrauch von Umlauten, Bindestrichen, Leerzeichen etc. beachten!**

| Element | Beschreibung |
| :--- | :--- |
| `config_plugin` | Dies ist das Hauptelement in der Konfigurationsdatei und muss genau einmal vorkommen. Es beinhaltet alle Konfigurationen. |
| `maximum_megabyte_per_file` | Die maximal erlaubte Dateigröße in Megabyte kann hier im Parameter `mb` festgelegt werden. Überschreitet der Datei-Upload diese Größe, wird eine Fehlermeldung zurückgegeben. |
| `step_to_close` | Diese Codeblöcke zeichnen jeweils genau einen Schritt aus, der auf der Benutzeroberfläche zum Schließen zur Auswahl stehen soll. Der Parameter `name` gibt den Namen des zu schließenden Schritts an. |
| `condition` | Mit diesen Unterelementen von `step_to_close` lassen sich die Vorbedingungen zum Schließen des jeweiligen Schritts angeben. Dafür wird mit den Parametern `stepname` und `status` der geforderte Zustand eines anderen Schritts angegeben. Der Status wird immer groß geschrieben. |

### Vorbedingungen einrichten

Der in `stepname` angegebene Schritt ist ein anderer Schritt, der in jedem Vorgang existieren muss, in dem der vom Nutzer ausgewählte Schritt geschlossen werden soll. Dieser muss zusätzlich in einem bestimmten Status sein. Dieser wird aus folgender Liste ausgewählt:

| Status | Bezeichnung |
| :--- | :--- |
| `DEACTIVATED` | Deaktiviert |
| `DONE` | Abgeschlossen |
| `ERROR` | Fehler |
| `INWORK` | In Bearbeitung |
| `LOCKED` | Gesperrt |
| `OPEN` | Offen |

## Bedienung des Plugins

Wenn das Plugin korrekt installiert und konfiguriert wurde, ist es innerhalb des Menüpunktes `Workflow` zu finden.

<!---
Hier ein Bildschirmfoto vom Plugin einfügen
-->

Zunächst wird eine Excel-Datei im `XLS`- oder im `XLSX`-Format hochgeladen, um die gewünschten Prozesse festzulegen. Diese beinhaltet eine Auflistung aller IDs der gewünschten Prozesse.

Am einfachsten bekommen Sie eine solche Datei, wenn Sie auf der Seite `Vorgänge suchen` eine Liste mit den von Ihnen gewünschten Prozessen zusammenstellen und das Ergebnis von der Seite `Vorgänge` in eine Excel-Datei exportieren.

**Wichtig:** Achten Sie darauf, dass für Spalte 1 die `Vorgang-ID` ausgewählt ist, da sonst die Datei nicht die erforderlichen IDs beinhaltet und nicht kompatibel zu diesem Plugin ist.

<!---
Hier optional ein Bildschirmfoto von einer korrekten Excel-Datei einfügen
-->

Als nächstes wird der zu schließende Schritt ausgewählt. Dafür steht ein Drop-Down-Menü zur Verfügung, welches alle in der Konfigurationsdatei angegebenen Schritte anbietet.

<!---
Hier optional ein Bildschirmfoto vom ausgeklappten Drop-Down-Menü anzeigen
-->

Klicken Sie nun auf `Datei hochladen`.

Wenn alle Vorbedingungen erfüllt sind, wird jetzt der soeben ausgewählte Schritt in allen in der Excel-Datei angegebenen Prozessen geschlossen. Sollte es zu Fehlern kommen, wird dies direkt angezeigt. Sie können optional die Liste der Fehlerbeschreibungen als Excel-Datei herunterladen.

<!---
Hier optional ein Bildschirmfoto von Fehlermeldungen anzeigen
-->
