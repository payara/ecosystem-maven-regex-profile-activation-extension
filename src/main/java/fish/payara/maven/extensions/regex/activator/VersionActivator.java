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

import org.apache.maven.artifact.versioning.DefaultArtifactVersion;
import org.apache.maven.artifact.versioning.InvalidVersionSpecificationException;
import org.apache.maven.artifact.versioning.VersionRange;
import org.apache.maven.model.Profile;
import org.apache.maven.model.building.ModelProblemCollector;
import org.apache.maven.model.profile.ProfileActivationContext;
import org.apache.maven.model.profile.activation.ProfileActivator;
import org.codehaus.plexus.component.annotations.Component;
import org.codehaus.plexus.component.annotations.Requirement;
import org.codehaus.plexus.logging.Logger;

@Component(role = ExtensionActivator.class, hint = "version-range", description = "Version Range Profile Activator")
public class VersionActivator implements ProfileActivator {
    @Requirement
    private Logger logger;

    @Override
    public boolean isActive(Profile profile, ProfileActivationContext context, ModelProblemCollector problems) {
        VersionRange range = parseSpec(profile);
        String value = getPropertyValue(context, profile.getActivation().getProperty().getName());
        if (value == null || value.isEmpty()) {
            logger.warn("Did not find value of property "+profile.getActivation().getProperty().getName());
            return false;
        }
        DefaultArtifactVersion version = new DefaultArtifactVersion(value);
        boolean result = range.containsVersion(version);
        if (logger.isDebugEnabled()) {
            logger.debug(String.format("Comparing %s against %s: %s", version, range, result ? "matches":"doesn't match"));
        }
        return result;
    }

    private String getPropertyValue(ProfileActivationContext context, String name) {
        return PropertyResolver.get(context, logger).resolve(name);
    }

    @Override
    public boolean presentInConfig(Profile profile, ProfileActivationContext context, ModelProblemCollector problems) {
        return parseSpec(profile) != null;
    }

    private VersionRange parseSpec(Profile profile) {
        if (profile.getActivation() == null || profile.getActivation().getProperty() == null) {
            return null;
        }
        String versionSpec = profile.getActivation().getProperty().getValue();
        if (versionSpec != null) {
            if (versionSpec.startsWith("=")) {
                versionSpec = versionSpec.substring(1);
            } else {
                return null;
            }
            try {
                return VersionRange.createFromVersionSpec(versionSpec);
            } catch (InvalidVersionSpecificationException e) {
                logger.warn("Invalid version range activation property "+versionSpec, e);
            }
        }
        return null;
    }
}
