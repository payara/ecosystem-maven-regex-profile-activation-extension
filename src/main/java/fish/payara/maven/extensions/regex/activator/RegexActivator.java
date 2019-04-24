/*
 * Copyright (c) [2019] Payara Foundation and/or its affiliates. All rights reserved.
 *
 * The contents of this file are subject to the terms of either the GNU
 * General Public License Version 2 only ("GPL") or the Common Development
 * and Distribution License("CDDL") (collectively, the "License").  You
 * may not use this file except in compliance with the License.  You can
 * obtain a copy of the License at
 * https://github.com/payara/Payara/blob/master/LICENSE.txt
 * See the License for the specific
 * language governing permissions and limitations under the License.
 *
 * When distributing the software, include this License Header Notice in each
 * file and include the License file at glassfish/legal/LICENSE.txt.
 *
 * GPL Classpath Exception:
 * The Payara Foundation designates this particular file as subject to the "Classpath"
 * exception as provided by the Payara Foundation in the GPL Version 2 section of the License
 * file that accompanied this code.
 *
 * Modifications:
 * If applicable, add the following below the License Header, with the fields
 * enclosed by brackets [] replaced by your own identifying information:
 * "Portions Copyright [year] [name of copyright owner]"
 *
 * Contributor(s):
 * If you wish your version of this file to be governed by only the CDDL or
 * only the GPL Version 2, indicate your decision by adding "[Contributor]
 * elects to include this software in this distribution under the [CDDL or GPL
 * Version 2] license."  If you don't indicate a single choice of license, a
 * recipient has the option to distribute your version of this file under
 * either the CDDL, the GPL Version 2 or to extend the choice of license to
 * its licensees as provided above.  However, if you add GPL Version 2 code
 * and therefore, elected the GPL Version 2 license, then the option applies
 * only if the new code is made subject to such option by the copyright
 * holder.
 */
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
                    String relativePath = project.getModel().getParent().getRelativePath().replace("pom.xml", "");
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