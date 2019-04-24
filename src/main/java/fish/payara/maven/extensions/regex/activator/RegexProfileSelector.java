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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import org.apache.maven.model.Profile;
import org.apache.maven.model.building.ModelProblemCollector;
import org.apache.maven.model.profile.DefaultProfileSelector;
import org.apache.maven.model.profile.ProfileActivationContext;
import org.apache.maven.model.profile.ProfileSelector;
import org.apache.maven.model.profile.activation.ProfileActivator;
import org.codehaus.plexus.component.annotations.Component;
import org.codehaus.plexus.component.annotations.Requirement;
import org.codehaus.plexus.logging.Logger;

@Component(role = ProfileSelector.class, hint = "default")
public class RegexProfileSelector extends DefaultProfileSelector {

    @Requirement
    private Logger logger;

    @Requirement(role = RegexActivator.class)
    protected List<ProfileActivator> activatorList = new ArrayList<>();

    @Override
    public List<Profile> getActiveProfiles(Collection<Profile> profiles, ProfileActivationContext context,
            ModelProblemCollector problems) {
        List<Profile> customList = new ArrayList<>(profiles.size());
        for (Profile profile : profiles) {
            if (isActive(profile, context, problems)) {
                customList.add(profile);
            }
        }
        List<Profile> defaultList = super.getActiveProfiles(profiles, context, problems);
        ArrayList<Profile> resolvedList = new ArrayList<>();
        resolvedList.addAll(customList);
        resolvedList.addAll(defaultList);
        if (logger.isDebugEnabled() && resolvedList.size() > 0) {
            logger.debug("Selected profiles: " + Arrays.toString(resolvedList.toArray()));
        }
        return resolvedList;
    }

    /**
     * Called instead of parent isActive to make sure that an OR gate is used for
     * profile activation.
     * 
     * @param profile  the profile to check
     * @param context  the environment to use in checking the profile
     * @param problems aggregator for build problems
     * 
     * @return if the profile is active.
     */
    protected boolean isActive(Profile profile, ProfileActivationContext context, ModelProblemCollector problems) {
        boolean isActive = false;
        for (ProfileActivator activator : activatorList) {
            if (activator.presentInConfig(profile, context, problems)) {
                isActive |= activator.isActive(profile, context, problems);
            }
        }
        return isActive;
    }

}