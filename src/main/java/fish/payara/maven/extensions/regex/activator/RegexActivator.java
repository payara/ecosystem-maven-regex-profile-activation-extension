package fish.payara.maven.extensions.regex.activator;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import org.apache.maven.model.Profile;
import org.apache.maven.model.building.ModelProblemCollector;
import org.apache.maven.model.io.xpp3.MavenXpp3Reader;
import org.apache.maven.model.profile.ProfileActivationContext;
import org.apache.maven.model.profile.activation.ProfileActivator;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.component.annotations.Component;
import org.codehaus.plexus.component.annotations.Requirement;
import org.codehaus.plexus.logging.Logger;
import org.codehaus.plexus.util.xml.pull.XmlPullParserException;

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

        logger.debug(String.format("Checking regex `%s` against property `%s` with value `%s`.", regex, propertyName,
                propertyValue));

        // If the property value isn't found
        if (propertyValue == null) {
            logger.debug(String.format("Property `%s` not found.", propertyName));
            return false;
        }

        boolean result = Pattern.matches(regex, propertyValue);

        if (result) {
            logger.debug(String.format("Property `%s` matches regex `%s`.", propertyName, regex));
        } else {
            logger.debug(String.format("Property `%s` doesn't match regex `%s`.", propertyName, regex));
        }

        return result;
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

    /**
     * Fetches a named property value from the given context.
     * 
     * @param context      the profile activation context to search.
     * @param propertyName the property name to fetch.
     */
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
        // If it's still null, try and hard load it from the project pom
        if (value == null) {
            value = getPropertyFromPom(context.getProjectDirectory(), propertyName);
        }
        return value;
    }

    /**
     * Will manually read the pom and any discoverable parent poms until a property
     * is found.
     */
    private String getPropertyFromPom(File projectDirectory, String propertyName) {

        // Look for the pom
        File pomFile = new File(projectDirectory, "pom.xml");
        if (!pomFile.exists()) {
            return null;
        }

        // Read the pom file
        try (InputStream is = new FileInputStream(pomFile)) {
            MavenProject project = new MavenProject(new MavenXpp3Reader().read(is));
            Object value = project.getProperties().get(propertyName);
            if (value != null) {
                return value.toString();
            } else {
                // If the pom contains a parent block with a relativePath, use that to
                // recursively search
                if (project.getModel() != null && project.getModel().getParent() != null) {
                    String relativePath = project.getModel().getParent().getRelativePath();
                    if (relativePath != null) {
                        return getPropertyFromPom(new File(projectDirectory, relativePath), propertyName);
                    }
                }
            }
        } catch (IOException | XmlPullParserException ex) {
            logger.debug("Error reading project pom file.", ex);
        }
        return null;
    }

}