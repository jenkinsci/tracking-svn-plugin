package hudson.plugins.trackingsvn;

import hudson.Extension;
import hudson.model.AbstractBuild;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.model.listeners.RunListener;
import hudson.scm.RevisionParameterAction;
import hudson.scm.SubversionTagAction;
import hudson.scm.SubversionSCM.SvnInfo;
import java.util.ArrayList;

@Extension
public class TrackingSVNRunListener extends RunListener<AbstractBuild> {

	public TrackingSVNRunListener() {
		super(AbstractBuild.class);
	}

	@Override
	public void onStarted(AbstractBuild r, TaskListener listener) {
		TrackingSVNProperty property = ((AbstractBuild<?, ?>) r).getProject()
				.getProperty(TrackingSVNProperty.class);
		if (property == null) {
			return;
		}

		Run run = property.getTrackedBuild();

		listener.getLogger().println("Tracking SVN of " + run.getFullDisplayName());

		SubversionTagAction tagAction = run
				.getAction(SubversionTagAction.class);
		if (tagAction == null) {
			throw new TrackingSVNException("Project " + property.getSourceProject()
					+ " is not an SVN project");
		}

		ArrayList<SvnInfo> revisions = new ArrayList<SvnInfo>();
		for (SvnInfo info: tagAction.getTags().keySet()) {
			if (!property.isURLIgnored(info.url)) {
				revisions.add(info);
			}
		}
		RevisionParameterAction action = new RevisionParameterAction(revisions);
		r.addAction(action);

		r.addAction(new TrackingSVNAction(run));
	}

}
