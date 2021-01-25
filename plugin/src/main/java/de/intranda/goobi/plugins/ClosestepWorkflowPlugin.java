package de.intranda.goobi.plugins;

import de.sub.goobi.config.ConfigPlugins;
import de.sub.goobi.helper.enums.StepStatus;
import java.io.Serializable;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.log4j.Log4j2;
import net.xeoh.plugins.base.annotations.PluginImplementation;
import org.apache.commons.configuration.SubnodeConfiguration;
import org.apache.commons.configuration.XMLConfiguration;
import org.goobi.production.enums.PluginType;
import org.goobi.production.plugin.interfaces.IPlugin;
import org.goobi.production.plugin.interfaces.IWorkflowPlugin;

/**
 * This plugin is to close steps only when a list of specified other steps have reached a certain status.
 * 
 * @author maurice
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
     * The list of steps that should be closed by this plugin, read from configuration file
     */
    private List<CloseableStep> closeableSteps;

    /**
     * Information about all loaded configuration, can be shown in the GUI.
     */
    @Setter
    @Getter
    private String htmlInformation;

    /**
     * The constructor to get a plugin object. It loads the configuration from the XML file and initializes the list of steps that should be closed.
     */
    public ClosestepWorkflowPlugin() {
        log.info("Closestep workflow plugin started.");
        try {
            this.loadConfiguration();
            this.htmlInformation = this.toString();
        } catch (ParseException pe) {
            this.htmlInformation = pe.getMessage();
        }
    }

    /**
     * Loads all steps that should be closed with their conditions from the XML file and builds the closeableSteps object
     * 
     * @throws ParseException When there is missing / wrong content in the XML file
     */
    public void loadConfiguration() throws ParseException {
        XMLConfiguration configuration = ConfigPlugins.getPluginConfig(this.title);
        List<?> list = configuration.configurationsAt("config_plugin");
        if (list.size() != 1) {
            throw new ParseException("There is no unique root element in the XML file (" + list.size() + " root elements in file)!", 0);
        }
        SubnodeConfiguration configuration2 = (SubnodeConfiguration) list.get(0);
        List<?> stepsToClose = configuration2.configurationsAt("step_to_close");
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
                StepStatus stepToCloseStatus = parseStepStatus(conditionConfiguration.getString("@status"));
                conditions.add(new CloseCondition(stepToCloseName, stepToCloseStatus));
                conditionIndex++;
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
    private StepStatus parseStepStatus(String status) throws ParseException {
        if (status == null || status.length() == 0) {
            throw new ParseException("The status string is null or empty!", 0);
        }
        switch (status) {
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
     * Returns the string representation of the whole list of steps that shoule be closed
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
}