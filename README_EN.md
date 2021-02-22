---
Description: >-
	This workflow plugin allows you to automatically close a step in multiple processes.
---

# Automatic closing of steps

## Introduction

This workflow plugin makes it possible to close a specific step in several processes at the same time. The steps available for this are defined beforehand in a configuration file and are available for selection later on the web interface. The processes in which the selected step is to be closed are specified via file upload.

## Overview

<!---
Existiert das Projekt schon auf Github? Wenn es angelegt ist, den Pfad nochmal überprüfen
Passt die ältere unterstützte Goobi-Version?
-->
| Details | |
| :--- | :--- |
| Identifier          | intranda\_workflow\_closestep |
| Source code         | [https://github.com/intranda/goobi-plugin-workflow-closestep](https://github.com/intranda/goobi-plugin-workflow-closestep) |
| Licence             | GPL 2.0 or newer |
| Compatibility       | Goobi workflow 20.06 |
| Documentation date  | 15.02.2021 |

## Installation

The following files must be installed for the installation:

<!---
Wie entstehen diese Dateien? Braucht das Plugin auch die GUI-Datei?
-->

```bash
/opt/digiverso/goobi/plugins/workflow/plugin_intranda_workflow_closestep.jar
/opt/digiverso/goobi/plugins/GUI/plugin_intranda_workflow_closestep-GUI.jar
```

To set the closable steps for the plugin, the following configuration file must be created and customized:

```bash
/opt/digiverso/goobi/config/plugin_intranda_workflow_closestep.xml
```

The content of this configuration file looks like this:

```markup
<config_plugin>
	<maximum_megabyte_per_file mb="5" />
	<!-- The status may be LOCKED, OPEN, INWORK, DONE, ERROR or DEACTIVATED -->
	<step_to_close name="Check biographies">
		<condition stepname="Importing the images" status="OPEN" />
		<condition stepname="Archiving" status="DONE" />
	</step_to_close>
	<step_to_close name="Quality check">
		<condition stepname="Importing the images" status="OPEN" />
		<condition stepname="Archiving" status="DONE" />
	</step_to_close>
	<step_to_close name="Export in viewer">
		<condition stepname="Archiving" status="DONE" />
		<condition stepname="Importing the images" status="DONE" />
		<condition stepname="Bibliographic record" status="DONE" />
	</step_to_close>
</config_plugin>
```

Additionally, the maximum file size (in megabytes) for the upload can be specified. However, this is probably relevant in the rarest cases.

To use this plugin, the user must have the correct role permission.

<!---
Hier noch die richtige Rolle eintragen und die Bildschirmfotos machen
-->

![Without correct permission this plugin isn't available](../.gitbook/assets/intranda_workflow_closestep1_de.png)

Therefore, please assign role `Plugin_Goobi_Closestep` to the group.

![Correctly assigned role for the users](../.gitbook/assets/intranda_workflow_closestep2_de.png)

## Explanation of the configuration options

**Please pay attention to the correct capitalization and use of umlauts, hyphens, spaces etc. in all given step names!**

| Element | Description |
| :--- | :--- |
| `config_plugin` | This is the main element in the configuration file and must occur exactly once. It contains all configurations. |
| `maximum_megabyte_per_file` | The maximum allowed file size in megabytes can be specified here in the `mb` parameter. If the file upload exceeds this size, an error message is returned. |
| `step_to_close` | These code blocks each draw exactly one step to be available for selection on the user interface for closing. The `name` parameter specifies the name of the step to be closed. |
| `condition` | These sub-elements of `step_to_close` can be used to specify the preconditions for closing the respective step. For this purpose, the parameters `stepname` and `status` are used to specify the required state of another step. The status is always written in capital letters. |

### Set up preconditions

The step specified in `stepname` is another step that must exist in each operation where the step selected by the user is to be closed. This step must also be in a certain state. This is selected from the following list:

| Status | Description |
| :--- | :--- |
| `DEACTIVATED` | Deactivated |
| `DONE` | Completed |
| `ERROR` | Error |
| `INWORK` | In process |
| `LOCKED` | Locked |
| `OPEN` | Open |

## Operation of the plugin

If the plugin has been installed and configured correctly, it can be found within the 'Workflow' menu item.

<!---
Hier ein Bildschirmfoto vom Plugin einfügen
-->

First, an Excel file is uploaded in `XLS` or `XLSX` format to specify the desired processes. This contains a listing of all IDs of the desired processes.

The easiest way to get such a file is to compile a list of the processes you want on the 'Search processes' page and export the result from the 'Processes' page to an Excel file.

**Important:** Make sure that `Select all fields` is selected, otherwise the file will not contain the required IDs in the second column and will not be compatible with this plugin.

<!---
Hier optional ein Bildschirmfoto von einer korrekten Excel-Datei einfügen
-->

Next, the step to be closed is selected. A drop-down menu is available for this purpose, which offers all the steps specified in the configuration file.

<!---
Hier optional ein Bildschirmfoto vom ausgeklappten Drop-Down-Menü anzeigen
-->

Now click on `Upload file`.

If the Excel file does not contain any task IDs, a corresponding error message is now displayed.

Otherwise, a list with all processes will now appear. Depending on whether a step can be closed, is already closed, or cannot be closed for certain reasons, an appropriate box is now displayed. If there are error messages, the list of error messages within an operation is expandable.

If there are closable steps, these are closed with a click on "Close steps" and the boxes for the corresponding steps change from "Can be closed" to "Is closed".

You can optionally download the list of status and error descriptions as an Excel file.

<!---
Hier optional ein Bildschirmfoto von Fehlermeldungen anzeigen
-->
