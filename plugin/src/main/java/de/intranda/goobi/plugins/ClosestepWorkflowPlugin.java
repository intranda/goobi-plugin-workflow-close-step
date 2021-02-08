package de.intranda.goobi.plugins;

import de.sub.goobi.helper.CloseStepHelper;
import de.sub.goobi.helper.Helper;
import de.sub.goobi.helper.enums.StepStatus;
import de.sub.goobi.persistence.managers.ProcessManager;

import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.Serializable;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.faces.context.FacesContext;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.Part;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.log4j.Log4j2;

import net.xeoh.plugins.base.annotations.PluginImplementation;

import org.apache.commons.configuration.ConfigurationException;
import org.apache.commons.configuration.SubnodeConfiguration;
import org.apache.commons.configuration.XMLConfiguration;
import org.apache.commons.configuration.reloading.FileChangedReloadingStrategy;
import org.apache.commons.configuration.tree.xpath.XPathExpressionEngine;
import org.apache.poi.hssf.OldExcelFormatException;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import org.goobi.beans.Step;
import org.goobi.production.enums.PluginType;
import org.goobi.production.plugin.interfaces.IPlugin;
import org.goobi.production.plugin.interfaces.IWorkflowPlugin;

/**
 * This plugin is to close steps only when a list of specified other steps have reached a certain status.
 *
 * @author Maurice Mueller
 */
@PluginImplementation
@Log4j2
public class ClosestepWorkflowPlugin implements IWorkflowPlugin, IPlugin, Serializable {

    /**
     * The serial version UID
     */
    public static final long serialVersionUID = 1L;

    /**
     * The title of this plugin
     */
    @Getter
    private final String title = "intranda_workflow_closestep";

    /**
     * The configuration file name for this plugin. The directory should be set by helper classes.
     */
    @Getter
    public static final String CONFIGURATION_FILE = "plugin_intranda_workflow_closestep.xml";

    /**
     * The maximum file size in megabyte. This is checked by the validator.
     */
    @Getter
    public static int MAXIMUM_FILE_SIZE_IN_MB;

    /**
     * The File object that was uploaded by the user. This is null before the user uploaded a file.
     */
    @Setter
    @Getter
    private Part file;

    /**
     * The File name of the uploaded file. Is null when there is no file.
     */
    @Setter
    @Getter
    private String fileName;

    /**
     * The status message for the UI. Contains some information about the upload success or failure.
     */
    @Setter
    @Getter
    private String uploadStatusMessage;

    /**
     * The status message for the UI. Contains some information about the read-in success or failure.
     */
    @Setter
    @Getter
    private String readInStatusMessage;

    /**
     * A list of lists to store for each step that should be closed a list of errors (or nothing)
     */
    @Getter
    private List<List<String>> errorMessages;

    /**
     * A list to store the name of each step that has missing conditions
     */
    @Getter
    private List<String> errorMessageTitles;

    /**
     * The number of error message titles, this is also the number of processes in which steps can not be closed
     */
    @Getter
    private int numberOfErrorMessageTitles;

    /**
     * This list indicates which of the error messages are open or closed on the GUI
     */
    @Setter
    @Getter
    private List<Boolean> expandedErrorMessages;
    /**
     * The process ids from the excel file to mind when closing steps.
     */
    @Getter
    private List<Integer> processIds;

    /**
     * The list of steps that should be closed by this plugin, read from configuration file
     */
    private List<CloseableStep> closeableSteps;

    /**
     * Information about all loaded configuration, can be shown in the GUI.
     */
    @Setter
    @Getter
    private static XMLConfiguration configuration;

    /**
     * The constructor to get a plugin object. It loads the configuration object and the configuration from the XML file and initializes the list of
     * steps that should be closed.
     */
    public ClosestepWorkflowPlugin() {
        this.uploadStatusMessage = "No file uploaded.";
        this.readInStatusMessage = "No file uploaded.";
        log.info("Closestep workflow plugin started.");
        try {
            this.loadConfiguration();
            this.loadXML();
        } catch (ParseException pe) {
            this.uploadStatusMessage = pe.getMessage();
        } catch (ConfigurationException ce) {
            this.uploadStatusMessage = ce.getMessage();
        }
    }

    /**
     * Loads the configuration object to read the XML file
     */
    public void loadConfiguration() throws ConfigurationException {
        if (configuration == null) {
            configuration = new XMLConfiguration();
            configuration.setDelimiterParsingDisabled(true);
            configuration.load(new Helper().getGoobiConfigDirectory() + CONFIGURATION_FILE);
            configuration.setReloadingStrategy(new FileChangedReloadingStrategy());
            configuration.setExpressionEngine(new XPathExpressionEngine());
        }
    }

    /**
     * Loads all steps that should be closed with their conditions from the XML file and builds the closeableSteps object
     *
     * @throws ParseException When there is missing / wrong content in the XML file
     */
    public void loadXML() throws ParseException {
        this.closeableSteps = new ArrayList<CloseableStep>();
        // Load maximum megabyte per file
        try {
            SubnodeConfiguration maximum_megabyte = (SubnodeConfiguration) configuration.configurationsAt("//maximum_megabyte_per_file").get(0);
            int megabyte = Integer.parseInt(maximum_megabyte.getString("@mb"));
            MAXIMUM_FILE_SIZE_IN_MB = megabyte;
        } catch (Exception e) {
            MAXIMUM_FILE_SIZE_IN_MB = 10;
        }
        // Load steps to close
        List<?> stepsToClose = configuration.configurationsAt("//step_to_close");
        int stepIndex = 0;
        while (stepIndex < stepsToClose.size()) {
            SubnodeConfiguration stepConfiguration = (SubnodeConfiguration) stepsToClose.get(stepIndex);
            String stepName = stepConfiguration.getString("@name");
            if (stepName == null || stepName.length() == 0) {
                throw new ParseException("Step name is missing in step " + (stepIndex + 1) + "!", 0);
            }
            List<?> conditionsRawData = stepConfiguration.configurationsAt("condition");
            List<CloseCondition> conditions = new ArrayList<>();
            int conditionIndex = 0;
            while (conditionIndex < conditionsRawData.size()) {
                SubnodeConfiguration conditionConfiguration = (SubnodeConfiguration) conditionsRawData.get(conditionIndex);
                String stepToCloseName = conditionConfiguration.getString("@stepname");
                if (stepToCloseName == null || stepToCloseName.length() == 0) {
                    throw new ParseException("Step name is missing for condition " + conditionIndex + " in step-to-close: " + stepName, 0);
                }
                StepStatus stepToCloseStatus = this.convertStringToStatus(conditionConfiguration.getString("@status"));
                conditions.add(new CloseCondition(stepToCloseName, stepToCloseStatus));
                conditionIndex++;
            }
            this.closeableSteps.add(new CloseableStep(stepName, conditions));
            stepIndex++;
        }
    }

    /**
     * Parses a string representation of a step status to the fitting StepStatus object and returns this object
     *
     * @param status The status as string representation (read from XML-file)
     * @return The parsed StepStatus object
     * @throws ParseException When the string is null, empty or not a valid StepStatus representation
     */
    private StepStatus convertStringToStatus(String status) throws ParseException {
        if (status == null || status.length() == 0) {
            throw new ParseException("The status string is null or empty!", 0);
        }
        switch (status.toUpperCase()) {
            case "LOCKED":
                return StepStatus.LOCKED;
            case "OPEN":
                return StepStatus.OPEN;
            case "INWORK":
                return StepStatus.INWORK;
            case "DONE":
                return StepStatus.DONE;
            case "ERROR":
                return StepStatus.ERROR;
            case "DEACTIVATED":
                return StepStatus.DEACTIVATED;
            default:
                throw new ParseException("This string is no valid step status:\"" + status + "\"!", 0);
        }
    }

    /**
     * Converts a status enum-item to it's string representation
     *
     * @param status The status to get the string for
     */
    private String convertStatusToString(StepStatus status) {
        switch (status) {
            case LOCKED:
                return "LOCKED";
            case OPEN:
                return "OPEN";
            case INWORK:
                return "INWORK";
            case DONE:
                return "DONE";
            case ERROR:
                return "ERROR";
            case DEACTIVATED:
                return "DEACTIVATED";
            default:
                return "UNKNOWN STATUS";
        }
    }

    /**
     * Returns the plugin type of this plugin. This should always be a workflow plugin
     *
     * @return The type of this plugin (always PluginType.Workflow)
     */
    @Override
    public PluginType getType() {
        return PluginType.Workflow;
    }

    /**
     * Returns the URL to the GUI (user interface page)
     *
     * @return The URL as simple String
     */
    @Override
    public String getGui() {
        return "/uii/plugin_workflow_closestep.xhtml";
    }

    /**
     * Returns the string representation of the whole list of steps that should be closed
     *
     * @return The string representation of all steps
     */
    public String toString() {
        if (this.closeableSteps == null) {
            return "";
        }
        StringBuffer sb = new StringBuffer();
        int i = 0;
        while (i < this.closeableSteps.size()) {
            sb.append(this.closeableSteps.get(i).toString());
            i++;
        }
        return sb.toString();
    }

    /**
     * Uploads a file. Gets the file from the UI and stores the data in the 'objects' file and 'fileName'
     *
     * @return The status message, visible for the user in the UI
     */
    public String uploadExcelFile() {
        this.setUploadedFileName();
        if (!this.validate()) {
            this.file = null;
            this.fileName = null;
        }
        this.readExcelFile();
        this.checkConditionsOrCloseSteps(false);
        return "";
    }

    /**
     * Initializes the file name when there already exists an uploaded file. Otherwise the fileName will be set to 'null'
     */
    public void setUploadedFileName() {
        if (this.file == null) {
            return;
        }
        String header = this.file.getHeader("content-disposition");
        if (header == null) {
            return;
        }
        String[] headerParts = header.split(";");
        String tmp;
        int headerIndex = 0;
        while (headerIndex < headerParts.length) {
            tmp = headerParts[headerIndex];
            if (tmp.trim().startsWith("filename")) {
                tmp = tmp.substring(tmp.indexOf('=') + 1, tmp.length());
                tmp = tmp.trim().replace("\"", "");
                this.fileName = tmp;
            }
            headerIndex++;
        }
        return;
    }

    /**
     * Validates the file and sets the status message to the UI in case of wrong file properties.
     *
     * @return true When the file was validated successfully
     */
    public boolean validate() {
        if (this.file == null || this.file.getSize() <= 0 || this.file.getContentType().isEmpty()) {
            this.uploadStatusMessage = "Please select a valid file.";
            return false;
        } else if (!this.fileName.endsWith("xls") && !this.fileName.endsWith("xlsx")) {
            this.uploadStatusMessage = "Please select an excel file (should end with \".xls\" or \".xlsx\").";
            this.uploadStatusMessage += " Curent file name: " + this.fileName + ".";
            return false;
        } else if (this.file.getSize() > 1000 * 1000 * MAXIMUM_FILE_SIZE_IN_MB) {
            this.uploadStatusMessage = "The file size is too big. Maximum file size: " + MAXIMUM_FILE_SIZE_IN_MB + "MB.";
            this.uploadStatusMessage += " Current file size: " + (this.file.getSize() / 1000 / 1000) + " MB.";
            return false;
        }
        this.uploadStatusMessage = "Uploaded successfully: " + this.fileName;
        return true;
    }

    /**
     * Reads in the excel file, collects all process ids (all numeric cells and all text cells containing a number) and generates a new message
     * string.
     *
     * @return true When the content of the file could be accepted
     */
    public boolean readExcelFile() {
        this.processIds = new ArrayList<>();
        Workbook workbook = null;
        try {
            FileInputStream file = (FileInputStream) (this.file.getInputStream());
            if (this.fileName.endsWith("xls")) {
                workbook = new HSSFWorkbook(file);
            } else if (this.fileName.endsWith("xlsx")) {
                workbook = new XSSFWorkbook(file);
            }
        } catch (IOException ioe) {
            this.readInStatusMessage = "Error while reading the excel file: " + ioe.getMessage();
            return false;
        } catch (OldExcelFormatException oefe) {
            this.readInStatusMessage = "The excel file seems to be too old: " + oefe.getMessage();
            return false;
        }
        if (workbook == null) {
            this.readInStatusMessage = "Error: while reading the excel file.";
            return false;
        }
        int numberOfSheets = workbook.getNumberOfSheets();
        int currentSheet = 0;
        while (currentSheet < numberOfSheets) {
            Sheet sheet = workbook.getSheetAt(currentSheet);
            if (sheet == null) {
                currentSheet++;
                continue;
            }
            int numberOfRows = sheet.getLastRowNum() + 1;
            int currentRow = 0;
            while (currentRow < numberOfRows) {
                Row row = sheet.getRow(currentRow);
                if (row == null) {
                    currentRow++;
                    continue;
                }
                int numberOfCells = row.getLastCellNum() + 1;
                int currentCell = 0;
                while (currentCell < numberOfCells) {
                    Cell cell = row.getCell(currentCell);
                    if (cell == null) {
                        currentCell++;
                        continue;
                    }
                    try {
                        this.processIds.add((int) (cell.getNumericCellValue()));
                    } catch (Exception e) {
                        try {
                            this.processIds.add(Integer.parseInt(cell.getStringCellValue()));
                        } catch (NumberFormatException nfe) {
                            // This was only a trial, when there is no number, this cell isn't relevant
                        }
                    }
                    currentCell++;
                }
                currentRow++;
            }
            currentSheet++;
        }
        this.readInStatusMessage = "Read process ids successfully! Following process ids will be used: " + this.processIds.toString();
        return true;
    }

    /**
     * Closes the specified steps when the button in the GUI was clicked
     *
     * @return An empty string until now
     */
    public String close() {
        this.checkConditionsOrCloseSteps(true);
        return "";
    }

    /**
     * Closes all fitting steps in the given processes. Generates a list with error messages
     *
     * @param close Should be true to close the steps, should be false to only get the error messages
     */
    public void checkConditionsOrCloseSteps(boolean close) {
        this.errorMessages = new ArrayList<>();
        this.errorMessageTitles = new ArrayList<>();
        // Check conditions in all processes
        int processIndex = 0;
        while (processIndex < this.processIds.size()) {
            org.goobi.beans.Process process = ProcessManager.getProcessById(this.processIds.get(processIndex));
            if (process != null) {
                List<String> errorsForProcess = new ArrayList<>();
                // Handle each closable step
                int closeableStepIndex = 0;
                while (closeableStepIndex < this.closeableSteps.size()) {
                    CloseableStep closeableStep = this.closeableSteps.get(closeableStepIndex);
                    int stepToCloseIndex = this.getIndexOfStepByTitleInProcess(process, closeableStep.getName());
                    if (stepToCloseIndex != -1) {
                        Step stepToClose = process.getSchritte().get(stepToCloseIndex);
                        // Handle each condition for that step
                        int conditionIndex = 0;
                        while (conditionIndex < closeableStep.getConditions().size()) {
                            CloseCondition condition = closeableStep.getConditions().get(conditionIndex);
                            int indexOfStepThatMustPerformCondition = this.getIndexOfStepByTitleInProcess(process, condition.getStepName());
                            // Check the step that must have reached a certain state
                            if (indexOfStepThatMustPerformCondition != -1) {
                                Step stepThatMustPerformCondition = process.getSchritte().get(indexOfStepThatMustPerformCondition);
                                if (stepThatMustPerformCondition.getBearbeitungsstatusEnum() == condition.getStatus()) {
                                    if (close) {
                                        CloseStepHelper.closeStep(stepToClose, Helper.getCurrentUser());
                                    }
                                } else {
                                    errorsForProcess.add("Cannot close \"" + closeableStep.getName() + "\" because step \"" + condition.getStepName() + "\" is not in state \""
                                            + this.convertStatusToString(condition.getStatus()) + "!");
                                }
                            } else {
                                errorsForProcess.add("Cannot close \"" + closeableStep.getName() + "\" because step \"" + condition.getStepName() + "\" does not exist in this process.");
                            }
                            conditionIndex++;
                        }
                    } else {
                        errorsForProcess.add("Cannot close \"" + closeableStep.getName() + "\" because step \"" + closeableStep.getName() + "\" does not exist in this process.");
                    }
                    closeableStepIndex++;
                }
                if (errorsForProcess.size() > 0) {
                    this.errorMessageTitles.add("Process: " + process.getTitel());
                    this.errorMessages.add(errorsForProcess);
                }
            } else {
                this.errorMessageTitles.add("Process with id number " + this.processIds.get(processIndex) + " does not exist.");
                List<String> detail = new ArrayList<>();
                detail.add("No further information");
                this.errorMessages.add(detail);
            }
            processIndex++;
        }
        this.expandedErrorMessages = new ArrayList<>();
        int index = 0;
        while (index < errorMessageTitles.size()) {
            this.expandedErrorMessages.add(false);
            index++;
        }
        this.numberOfErrorMessageTitles = this.errorMessageTitles.size();
        if (this.errorMessages.size() == 0) {
            this.readInStatusMessage = "Can close all chosed steps successfully.";
        } else {
            this.readInStatusMessage = "Not all steps can be closed. You can download an excel file containing all error messages.";
        }
    }

    /**
     * Searches for the step with the given title (in the process) and returns the index in the list. When there is no step with that title in the
     * list, it returns -1.
     *
     * @param process The process to search the step in
     * @param title The title to search for in the steps of that process
     * @return The index of the step, otherwise -1
     */
    public int getIndexOfStepByTitleInProcess(org.goobi.beans.Process process, String title) {
        List<Step> steps = process.getSchritte();
        int stepIndex = 0;
        while (stepIndex < steps.size()) {
            if (steps.get(stepIndex).getTitel().equals(title)) {
                return stepIndex;
            }
            stepIndex++;
        }
        return -1;
    }

    /**
     * Creates an excel file with all error messages. This method is called when the user presses the download button for getting the error messages.
     *
     * @throws IOException
     */
    public void downloadErrorMessagesAsExcelFile() throws IOException {
        // Create the workbook
        XSSFWorkbook workbook = new XSSFWorkbook();
        XSSFSheet sheet = workbook.createSheet("Error messages");
        // Fill the excel file with the error lines
        int rowCounter = 0;
        int stepCounter = 0;
        int errorCounter;
        while (stepCounter < this.errorMessages.size()) {
            Row titleRow = sheet.createRow(rowCounter);
            Cell titleCell = titleRow.createCell(0);
            titleCell.setCellValue(this.errorMessageTitles.get(stepCounter));
            rowCounter++;
            errorCounter = 0;
            while (errorCounter < this.errorMessages.get(stepCounter).size()) {
                Row row = sheet.createRow(rowCounter);
                Cell emptyCell = row.createCell(0);
                emptyCell.setCellValue("");
                Cell cell = row.createCell(1);
                cell.setCellValue(this.errorMessages.get(stepCounter).get(errorCounter));
                rowCounter++;
                errorCounter++;
            }
            stepCounter++;
        }
        // Create the byte array for the download
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        workbook.write(outputStream);
        byte[] bytes = outputStream.toByteArray();
        workbook.close();
        // Answer to the download request
        HttpServletResponse response = (HttpServletResponse) FacesContext.getCurrentInstance().getExternalContext().getResponse();
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setHeader("Content-Disposition", "attachment;filename=error_messages.xlsx");
        response.getOutputStream().write(bytes);
        response.getOutputStream().flush();
        response.getOutputStream().close();
        FacesContext.getCurrentInstance().responseComplete();
    }

    /**
     * Toggles the visibility of a shown error list when the user clicks on the title
     */
    public void toggleExpandedErrorMessage() {
        FacesContext facesContext = FacesContext.getCurrentInstance();
        Map<String, String> requestParameterMap = facesContext.getExternalContext().getRequestParameterMap();
        int id = Integer.parseInt(requestParameterMap.get("id"));
        boolean expanded = this.expandedErrorMessages.get(id).booleanValue();
        this.expandedErrorMessages.set(id, !expanded);
    }
}