package hudson.plugins.trackingsvn;

import hudson.Extension;
import hudson.Functions;
import hudson.Launcher;
import hudson.Util;
import hudson.model.*;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.BuildStepMonitor;
import hudson.tasks.Publisher;
import hudson.tasks.Recorder;
import org.kohsuke.stapler.DataBoundConstructor;

import java.io.IOException;
import java.util.*;

public class TriggerBuildPublisher extends Recorder {

	private final boolean triggerDownstreamProjects;
	private final String additionalProjects;
	private final String ignoredURLs;

	@DataBoundConstructor
	public TriggerBuildPublisher(boolean triggerDownstreamProjects, String additionalProjects, String ignoredURLs) {
		this.triggerDownstreamProjects = triggerDownstreamProjects;
		this.ignoredURLs = ignoredURLs;
		this.additionalProjects = Util.fixEmpty(additionalProjects);
	}

	public BuildStepMonitor getRequiredMonitorService() {
		return BuildStepMonitor.NONE;
	}

	@Override
	public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener) throws InterruptedException, IOException {
		if (build.getResult().isBetterOrEqualTo(Result.UNSTABLE)) {
			build.addAction(new TriggerBuildAction(build));
		}

		return true;
	}

	public boolean isTriggerDownstreamProjects() {
		return triggerDownstreamProjects;
	}

	public String getAdditionalProjects() {
		return additionalProjects;
	}

	public String getIgnoredURLs() {
		return ignoredURLs;
	}

	public boolean isURLIgnored(String url) {
		if (ignoredURLs == null) return false;
		for (String s : ignoredURLs.split("[, \n]")) {
			if (url.equals(Util.fixEmptyAndTrim(s))) return true;
		}
		return false;
	}

	public List<AbstractProject> getAdditionalProjectsList() {
		if (additionalProjects == null) return Collections.emptyList();

		StringTokenizer st = new StringTokenizer(additionalProjects, ",", false);
		List<AbstractProject> result = new ArrayList<AbstractProject>();
		while (st.hasMoreElements()) {
			AbstractProject proj = Hudson.getInstance().getItemByFullName(st.nextToken().trim(), AbstractProject.class);
			if (proj != null) result.add(proj);
		}
		return result;
	}

	@Extension
	public static class DescriptorImpl extends BuildStepDescriptor<Publisher> {

		@Override
		public boolean isApplicable(Class<? extends AbstractProject> jobType) {
			return AbstractProject.class.isAssignableFrom(jobType);
		}

		@Override
		public String getDisplayName() {
			return "Tracking SVN Trigger Build";
		}
	}
}
