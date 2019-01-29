package fish.payara.maven.extensions.regex.activator;

import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import org.apache.maven.model.Profile;
import org.apache.maven.model.building.ModelProblemCollector;
import org.apache.maven.model.profile.ProfileActivationContext;
import org.apache.maven.model.profile.activation.ProfileActivator;
import org.codehaus.plexus.component.annotations.Component;
import org.codehaus.plexus.component.annotations.Requirement;
import org.codehaus.plexus.logging.Logger;

@Component(role = RegexActivator.class, hint = "Regex Profile Activator")
public class RegexActivator implements ProfileActivator {

    @Requirement
    private Logger logger;

    @Override
    public boolean isActive(Profile profile, ProfileActivationContext context, ModelProblemCollector problems) {
        // Get property name and value
        String propertyName = profile.getActivation().getProperty().getName();
        String propertyValue = getProperty(context, propertyName);

        // Get regex
        String regex = profile.getActivation().getProperty().getValue();
        regex = regex.substring(1, regex.length() - 1);

        boolean result = Pattern.matches(regex, propertyValue);

        logger.debug(String.format("Checking regex `%s` against property `%s` with value `%s`. Result: %b.", regex,
                propertyName, propertyValue, result));

        return result;
    }

    private String getProperty(ProfileActivationContext context, String propertyName) {
        // Fetch from -D parameter first
        String value = context.getUserProperties().get(propertyName);
        // Then fetch from project properties
        if (value == null) {
            value = context.getProjectProperties().get(propertyName);
        }
        // Then fetch from system properties
        if (value == null) {
            value = context.getSystemProperties().get(propertyName);
        }
        return value;
    }

    @Override
    public boolean presentInConfig(Profile profile, ProfileActivationContext context, ModelProblemCollector problems) {
        try {
            // Check that the property activation exists
            String regex = profile.getActivation().getProperty().getValue();

            // Check that the pattern is a valid regex
            if (!regex.matches("/.+/")) {
                return false;
            }
            regex = regex.substring(1, regex.length() - 1);

            // Check the regex is valid
            Pattern.compile(regex);
        } catch (NullPointerException | PatternSyntaxException ex) {
            return false;
        }
        return true;
    }

}