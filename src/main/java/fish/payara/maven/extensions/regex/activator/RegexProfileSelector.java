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
     * Called instead of parent isActive to make sure that an OR gate is used for profile activation.
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