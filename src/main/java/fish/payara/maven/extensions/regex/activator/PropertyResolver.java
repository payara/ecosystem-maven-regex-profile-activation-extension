/*
 *    DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 *    Copyright (c) [2019] Payara Foundation and/or its affiliates. All rights reserved.
 *
 *    The contents of this file are subject to the terms of either the GNU
 *    General Public License Version 2 only ("GPL") or the Common Development
 *    and Distribution License("CDDL") (collectively, the "License").  You
 *    may not use this file except in compliance with the License.  You can
 *    obtain a copy of the License at
 *    https://github.com/payara/Payara/blob/master/LICENSE.txt
 *    See the License for the specific
 *    language governing permissions and limitations under the License.
 *
 *    When distributing the software, include this License Header Notice in each
 *    file and include the License file at glassfish/legal/LICENSE.txt.
 *
 *    GPL Classpath Exception:
 *    The Payara Foundation designates this particular file as subject to the "Classpath"
 *    exception as provided by the Payara Foundation in the GPL Version 2 section of the License
 *    file that accompanied this code.
 *
 *    Modifications:
 *    If applicable, add the following below the License Header, with the fields
 *    enclosed by brackets [] replaced by your own identifying information:
 *    "Portions Copyright [year] [name of copyright owner]"
 *
 *    Contributor(s):
 *    If you wish your version of this file to be governed by only the CDDL or
 *    only the GPL Version 2, indicate your decision by adding "[Contributor]
 *    elects to include this software in this distribution under the [CDDL or GPL
 *    Version 2] license."  If you don't indicate a single choice of license, a
 *    recipient has the option to distribute your version of this file under
 *    either the CDDL, the GPL Version 2 or to extend the choice of license to
 *    its licensees as provided above.  However, if you add GPL Version 2 code
 *    and therefore, elected the GPL Version 2 license, then the option applies
 *    only if the new code is made subject to such option by the copyright
 *    holder.
 */

package fish.payara.maven.extensions.regex.activator;

import org.apache.maven.model.io.xpp3.MavenXpp3Reader;
import org.apache.maven.model.profile.ProfileActivationContext;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.logging.Logger;
import org.codehaus.plexus.util.xml.pull.XmlPullParserException;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

class PropertyResolver {
    private final ProfileActivationContext context;
    private final Logger logger;
    private Map<File, MavenProject> projectCache = new HashMap<>();
    private Map<String, String> cache = new HashMap<>();


    private PropertyResolver(ProfileActivationContext context, Logger logger) {
        this.context = context;
        this.logger = logger;
    }

    static final Pattern INTERPOLATION = Pattern.compile("\\$\\{([^\\}]+)\\}");

    /**
     * Fetches a named property value from current context.
     *
     * @param propertyName the property name to fetch.
     */
    String resolve(String propertyName) {
        return resolveFromCache(propertyName, null);
    }

    private String resolveFromCache(String propertyName, Set<String> parents) {
        if (parents != null && parents.contains(propertyName)) {
            throw new IllegalArgumentException("Recursive property definition involving " + propertyName);
        }

        if (!cache.containsKey(propertyName)) {
            cache.put(propertyName, resolve(propertyName, parents == null ? new HashSet<String>() : parents));
        }

        return cache.get(propertyName);
    }

    private String resolve(String propertyName, Set<String> parents) {

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

        // Interpolate any further property references in the value
        if (value != null) {
            Matcher interpolation = INTERPOLATION.matcher(value);
            StringBuffer sb = new StringBuffer();
            while (interpolation.find()) {
                String inner = interpolation.group(1);
                parents.add(propertyName);
                String replacement = resolveFromCache(inner, parents);
                if (replacement == null) {
                    replacement = "";
                }
                interpolation.appendReplacement(sb, replacement);
                parents.remove(propertyName);
            }
            interpolation.appendTail(sb);
            value = sb.toString();
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
            logger.warn("No project file found at "+pomFile);
            return null;
        }

        MavenProject project = null;
        if (!projectCache.containsKey(pomFile)) {
            project = readMavenProject(pomFile);
        }
        if (project == null) {
            return null;
        }

        Object value;
        if (propertyName.startsWith("project.")) {
            // Fetch project properties from the model class
            String fieldName = propertyName.substring(8, propertyName.length());
            value = getViaReflection(project.getModel(), fieldName);
            if (value == null && project.getModel().getParent() != null) {
                value = getViaReflection(project.getModel().getParent(), fieldName);
            }
        } else {
            value = project.getProperties().get(propertyName);
        }
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
        return null;
    }

    private MavenProject readMavenProject(File file) {
        try (InputStream is = new FileInputStream(file)) {
            MavenProject project = new MavenProject(new MavenXpp3Reader().read(is));
            return project;
        } catch (IOException | XmlPullParserException e) {
            logger.warn("Could not read "+file, e);
            return null;
        }
    }

    /**
     * Fetches a variable via reflection from a given object.
     * @param object the object containing the value
     * @param fieldName the name of the field
     * @return the value if found, or null otherwise.
     */
    private static Object getViaReflection(Object object, String fieldName) {
        try {
            Field field = object.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            return field.get(object);
        } catch (IllegalArgumentException | IllegalAccessException | NoSuchFieldException
                | SecurityException e) {
            return null;
        }
    }

    static PropertyResolver CACHED = null;

    /**
     * Get valid resolver for given context.
     * Since resolution is often repeated in same context by both activators, the resolver is cached.
     *
     * @param context
     * @param logger
     * @return
     */
    static PropertyResolver get(ProfileActivationContext context, Logger logger) {
        if (CACHED != null && CACHED.context.equals(context)) {
            return CACHED;
        }
        CACHED = new PropertyResolver(context, logger);
        return CACHED;
    }

}
