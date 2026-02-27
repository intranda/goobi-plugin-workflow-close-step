package de.intranda.goobi.plugins;

import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

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
import org.goobi.production.plugin.interfaces.IWorkflowPlugin;

import de.sub.goobi.helper.CloseStepHelper;
import de.sub.goobi.helper.Helper;
import de.sub.goobi.helper.enums.StepStatus;
import de.sub.goobi.persistence.managers.ProcessManager;
import jakarta.faces.context.FacesContext;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.Part;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.log4j.Log4j2;
import net.xeoh.plugins.base.annotations.PluginImplementation;

/**
 * This plugin is to close steps only when a list of specified other steps have reached a certain status.
 *
 * @author Maurice Mueller
 */
@PluginImplementation
@Log4j2
public class ClosestepWorkflowPlugin implements IWorkflowPlugin {

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
     * Error messages might be expanded by default to avoid expanding all of them manually. Or they are not expanded to make the list shorter.
     */
    private static final boolean EXPANDED_ERRORS_BY_DEFAULT = false;

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
     * This flag is true when the file was read and there are no process ids. The user gets the information message that process ids are expected in
     * the second column (in the excel file).
     */
    @Getter
    private boolean noProcessesFound = false;

    /**
     * A list of status messages. Each element has three elements. The process title, the process id and the status whether the step can or cannot be
     * closed in this process or it is already closed.
     */
    @Getter
    private List<String[]> statusMessages;

    /**
     * A list of status messages (merged as strings to list them in the GUI)
     */
    @Getter
    List<String> statusMessageStrings;

    /**
     * A list of lists to store for each process that cannot be closed the causing errors (not fulfilled preconditions).
     */
    @Getter
    private List<List<String>> errorMessages;

    /**
     * The flag to indicate whether there are errors and a warning should be shown in the GUI.
     */
    @Getter
    private boolean errorMessagesWarningEnabled;

    /**
     * This list indicates which of the processes are expandable on the GUI. Only the processes with error messages are expandable.
     */
    @Getter
    private List<Boolean> processExpandable;

    /**
     * This list indicates which of the processes are expanded on the GUI. Processes that are not expandable are never expanded.
     */
    @Setter
    @Getter
    private List<Boolean> processExpanded;

    /**
     * This list contains the state of each process.
     */
    @Getter
    private List<Integer> processStates;

    /**
     * The constant for closable steps.
     */
    @Getter
    public final int stateClosable = 0;

    /**
     * The constant for not closable steps.
     */
    @Getter
    public final int stateNotClosable = 1;

    /**
     * The constant for already closed steps.
     */
    @Getter
    public final int stateClosed = 2;

    /**
     * The process ids from the excel file to mind when closing steps.
     */
    @Getter
    private List<Integer> processIds;

    /**
     * The list of steps that should be closed by this plugin, read from configuration file
     */
    @Getter
    private List<CloseableStep> closeableSteps;

    /**
     * The flag that indicates whether the process of closing all possible steps is done
     */
    @Getter
    private boolean closingStepsDone = false;

    /**
     * The currently selected step
     */
    @Getter
    @Setter
    private String selectedStep;

    /**
     * Information about all loaded configuration, can be shown in the GUI.
     */
    @Setter
    @Getter
    private static XMLConfiguration configuration;

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
     * The constructor to get a plugin object. It loads the configuration object and the configuration from the XML file and initializes the list of
     * steps that should be closed.
     */
    public ClosestepWorkflowPlugin() {
        this.uploadStatusMessage = "";
        this.readInStatusMessage = "";
        log.info("Closestep workflow plugin started.");
        try {
            this.loadConfiguration();
            this.loadXML();
        } catch (ParseException | ConfigurationException ce) {
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
        this.closeableSteps = new ArrayList<>();
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
        for (int stepIndex = 0; stepIndex < stepsToClose.size(); stepIndex++) {
            SubnodeConfiguration stepConfiguration = (SubnodeConfiguration) stepsToClose.get(stepIndex);
            String stepName = stepConfiguration.getString("@name");
            if (stepName == null || stepName.length() == 0) {
                throw new ParseException("Step name is missing in step " + (stepIndex + 1), 0);
            }
            List<?> conditionsRawData = stepConfiguration.configurationsAt("condition");
            List<CloseCondition> conditions = new ArrayList<>();
            for (int conditionIndex = 0; conditionIndex < conditionsRawData.size(); conditionIndex++) {
                SubnodeConfiguration conditionConfiguration = (SubnodeConfiguration) conditionsRawData.get(conditionIndex);
                String stepToCloseName = conditionConfiguration.getString("@stepname");
                if (stepToCloseName == null || stepToCloseName.length() == 0) {
                    throw new ParseException("Step name is missing for condition " + conditionIndex + " in step-to-close: " + stepName, 0);
                }
                StepStatus stepToCloseStatus = this.convertStringToStatus(conditionConfiguration.getString("@status"));
                conditions.add(new CloseCondition(stepToCloseName, stepToCloseStatus));
            }
            this.closeableSteps.add(new CloseableStep(stepName, conditions));
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
            throw new ParseException("The status string is null or empty", 0);
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
                throw new ParseException("This string is no valid step status:\"" + status + "\"", 0);
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
     * Returns the string representation of the whole list of steps that should be closed
     *
     * @return The string representation of all steps
     */
    @Override
    public String toString() {
        if (this.closeableSteps == null) {
            return "";
        }
        StringBuffer sb = new StringBuffer();
        for (CloseableStep element : this.closeableSteps) {
            sb.append(element.toString());
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
        for (String headerPart : headerParts) {
            tmp = headerPart;
            if (tmp.trim().startsWith("filename")) {
                tmp = tmp.substring(tmp.indexOf('=') + 1, tmp.length());
                tmp = tmp.trim().replace("\"", "");
                this.fileName = tmp;
            }
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
        this.uploadStatusMessage = "";
        return true;
    }

    /**
     * Reads in the excel file, collects all process ids (all numeric cells and all text cells containing a number) and generates a new message
     * string.
     *
     * @return true When the content of the file could be accepted
     */
    public boolean readExcelFile() {
        this.readInStatusMessage = "";
        this.processIds = new ArrayList<>();
        Workbook workbook = null;
        try {
            FileInputStream file = (FileInputStream) (this.file.getInputStream());
            if (this.fileName.endsWith("xls")) {
                workbook = new HSSFWorkbook(file);
            } else if (this.fileName.endsWith("xlsx")) {
                workbook = new XSSFWorkbook(file);
            }
        } catch (NullPointerException | IOException ioe) {
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
        for (int currentSheet = 0; currentSheet < numberOfSheets; currentSheet++) {
            Sheet sheet = workbook.getSheetAt(currentSheet);
            if (sheet == null) {
                currentSheet++;
                continue;
            }
            int numberOfRows = sheet.getLastRowNum() + 1;
            for (int currentRow = 0; currentRow < numberOfRows; currentRow++) {
                Row row = sheet.getRow(currentRow);
                if (row == null) {
                    currentRow++;
                    continue;
                }
                Cell cell = row.getCell(1);
                if (cell == null) {
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
            }
        }
        this.noProcessesFound = (this.processIds.size() == 0);
        this.readInStatusMessage = "";
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
        this.statusMessages = new ArrayList<>();
        this.statusMessageStrings = new ArrayList<>();
        this.errorMessages = new ArrayList<>();
        this.processExpandable = new ArrayList<>();
        this.processExpanded = new ArrayList<>();
        this.processStates = new ArrayList<>();
        this.errorMessagesWarningEnabled = false;
        // Check conditions in all processes
        for (int processIndex = 0; processIndex < this.processIds.size(); processIndex++) {
            int processIdInt = this.processIds.get(processIndex);
            org.goobi.beans.Process process = ProcessManager.getProcessById(processIdInt);
            String processId = String.valueOf(processIdInt);// This is needed for the status message table
            if (process != null) {
                String processTitle = process.getTitel();// This is needed for the status message table
                List<String> errorsForProcess = new ArrayList<>();
                // Handle each closable step
                CloseableStep closeableStep = this.closeableSteps.stream()
                        .filter((step -> step.getName().equals(this.selectedStep)))
                        .findAny()
                        .get();
                boolean canBeClosed = true;
                boolean isAlreadyClosed = false;
                int stepToCloseIndex = this.getIndexOfStepByTitleInProcess(process, closeableStep.getName());
                if (stepToCloseIndex != -1) {
                    Step stepToClose = process.getSchritte().get(stepToCloseIndex);
                    isAlreadyClosed = stepToClose.getBearbeitungsstatusEnum() == StepStatus.DONE;
                    if (!isAlreadyClosed) {// Otherwise the step is already closed
                        // Handle each condition for that step
                        for (int conditionIndex = 0; conditionIndex < closeableStep.getConditions().size(); conditionIndex++) {
                            CloseCondition condition = closeableStep.getConditions().get(conditionIndex);
                            int indexOfStepThatMustPerformCondition = this.getIndexOfStepByTitleInProcess(process, condition.getStepName());
                            // Check the step that must have reached a certain state
                            if (indexOfStepThatMustPerformCondition != -1) {
                                Step stepThatMustPerformCondition = process.getSchritte().get(indexOfStepThatMustPerformCondition);
                                if (stepThatMustPerformCondition.getBearbeitungsstatusEnum() != condition.getStatus()) {
                                    canBeClosed = false;
                                    errorsForProcess.add("Cannot close \"" + closeableStep.getName() + "\" because step \"" + condition.getStepName()
                                            + "\" is not in state \""
                                            + this.convertStatusToString(condition.getStatus()) + "!");
                                }
                            } else {
                                canBeClosed = false;
                                errorsForProcess.add("Cannot close \"" + closeableStep.getName() + "\" because step \"" + condition.getStepName()
                                        + "\" does not exist in this process.");
                            }
                        }
                        if (canBeClosed && close) {
                            CloseStepHelper.closeStep(stepToClose, Helper.getCurrentUser());
                            canBeClosed = false;
                            isAlreadyClosed = true;
                        }
                    } else {
                        canBeClosed = false;
                        isAlreadyClosed = true;
                    }
                } else {
                    canBeClosed = false;
                    errorsForProcess.add("\"" + closeableStep.getName() + "\" does not exist in this process.");
                }
                String status = "\"" + closeableStep.getName() + "\" ";
                if (canBeClosed) {
                    status += "can be closed.";
                    this.processExpandable.add(false);
                    this.processExpanded.add(false);
                    this.processStates.add(this.stateClosable);
                } else if (isAlreadyClosed) {
                    status += "is already closed.";
                    this.processExpandable.add(false);
                    this.processExpanded.add(false);
                    this.processStates.add(this.stateClosed);
                } else {
                    this.errorMessagesWarningEnabled = true;
                    status += "can not be closed.";
                    this.processExpandable.add(true);
                    this.processExpanded.add(ClosestepWorkflowPlugin.EXPANDED_ERRORS_BY_DEFAULT);
                    this.processStates.add(this.stateNotClosable);
                }
                this.statusMessages.add(new String[] { processTitle, processId, status });
                this.statusMessageStrings.add(processTitle + ": " + status);
                this.errorMessages.add(errorsForProcess);
            } else {
                String idString = String.valueOf(processId);
                String title = "Unknown process";
                this.statusMessages.add(new String[] { "[No title]", idString, title });
                this.statusMessageStrings.add(title);
                List<String> errors = new ArrayList<>();
                errors.add("The process with id " + idString + " does not exist.");
                this.errorMessages.add(errors);
                this.processExpandable.add(true);
                this.processExpanded.add(true);
                this.processStates.add(this.stateNotClosable);
                this.errorMessagesWarningEnabled = true;
            }
        }
        if (this.errorMessages.size() == 0) {
            this.readInStatusMessage = "Can close all chosed steps successfully.";
        } else {
            this.readInStatusMessage = "Not all steps can be closed. You can download an excel file containing all error messages.";
        }
        if (close) {
            this.closingStepsDone = true;
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
        for (int stepIndex = 0; stepIndex < steps.size(); stepIndex++) {
            if (steps.get(stepIndex).getTitel().equals(title)) {
                return stepIndex;
            }
        }
        return -1;
    }

    /**
     * Creates an excel file with the status message for each process. This method is called when the user presses the download button for getting the
     * status messages.
     *
     * @throws IOException When there is an error with the output stream while downloading
     */
    public void downloadStatusMessagesAsExcelFile() throws IOException {
        XSSFWorkbook workbook = new XSSFWorkbook();
        XSSFSheet sheet = workbook.createSheet("Status messages");
        // Insert the header into the table
        Row titleRow = sheet.createRow(0);
        String[] header = new String[] { "Process Title", "Process ID", "Status / Errors" };
        for (int column = 0; column < header.length; column++) {
            Cell cell = titleRow.createCell(column);
            cell.setCellValue(header[column]);
        }
        int currentRow = 1;// 0 is the title row
        for (int processIndex = 0; processIndex < this.statusMessages.size(); processIndex++) {
            // Insert the status line for each process
            String[] message = this.statusMessages.get(processIndex);
            Row statusRow = sheet.createRow(currentRow);
            currentRow++;
            for (int column = 0; column < message.length; column++) {
                Cell cell = statusRow.createCell(column);
                cell.setCellValue(message[column]);
            }
            // Insert all errors for this process
            for (String element : this.errorMessages.get(processIndex)) {
                Row messageRow = sheet.createRow(currentRow);
                currentRow++;
                Cell emptyCell0 = messageRow.createCell(0);
                emptyCell0.setCellValue("");
                Cell emptyCell1 = messageRow.createCell(1);
                emptyCell1.setCellValue("");
                Cell errorMessageCell = messageRow.createCell(2);
                errorMessageCell.setCellValue(element);
            }
        }
        ClosestepWorkflowPlugin.downloadWorkbook(workbook, "status_messages.xlsx");
    }

    /**
     * Creates the byte stream and offers the workbook to download (independently of the content)
     *
     * @param workbook The workbook to download
     * @param fileName The predefined name that is shown in the download window for the user
     * @throws IOException When there is an error with the output stream while downloading
     */
    private static void downloadWorkbook(XSSFWorkbook workbook, String fileName) throws IOException {
        // Create the byte array for the download
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        workbook.write(outputStream);
        byte[] bytes = outputStream.toByteArray();
        workbook.close();
        // Answer to the download request
        HttpServletResponse response = (HttpServletResponse) FacesContext.getCurrentInstance().getExternalContext().getResponse();
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setHeader("Content-Disposition", "attachment;filename=" + fileName);
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
        boolean expanded = this.processExpanded.get(id).booleanValue();
        this.processExpanded.set(id, !expanded);
    }
}