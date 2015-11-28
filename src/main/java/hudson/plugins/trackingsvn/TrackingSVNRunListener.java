package hudson.plugins.trackingsvn;

import hudson.Extension;
import hudson.Util;
import hudson.console.HyperlinkNote;
import hudson.model.AbstractBuild;
import hudson.model.Fingerprint;
import hudson.model.FingerprintMap;
import hudson.model.TaskListener;
import hudson.model.listeners.RunListener;
import hudson.scm.RevisionParameterAction;
import hudson.scm.SubversionTagAction;
import hudson.scm.SubversionSCM.SvnInfo;
import hudson.tasks.Fingerprinter;
import jenkins.model.Jenkins;

import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

@Extension
public class TrackingSVNRunListener extends RunListener<AbstractBuild> {

    private static Logger logger = Logger.getLogger(TrackingSVNRunListener.class.getName());

    public TrackingSVNRunListener() {
        super(AbstractBuild.class);
    }

    @Override
    public void onStarted(AbstractBuild build, TaskListener listener) {
        RevisionParameterAction rpa = build.getAction(RevisionParameterAction.class);
        if (rpa != null) {
            return;
        }

        TrackingSVNProperty property = ((AbstractBuild<?, ?>) build).getProject()
                .getProperty(TrackingSVNProperty.class);
        if (property == null) {
            return;
        }

        AbstractBuild<?, ?> trackedBuild = (AbstractBuild<?, ?>) property.getTrackedBuild();

        listener.getLogger().println("Tracking " +
                HyperlinkNote.encodeTo('/' + trackedBuild.getParent().getUrl(), trackedBuild.getParent().getFullDisplayName()) +
                " " +
                HyperlinkNote.encodeTo('/' + trackedBuild.getUrl(), "#" + Integer.toString(trackedBuild.getNumber())));

        SubversionTagAction tagAction = trackedBuild.getAction(SubversionTagAction.class);
        while (tagAction == null) {
            TrackingSVNAction tsvnAction = trackedBuild.getAction(TrackingSVNAction.class);
            if (tsvnAction == null) {
                throw new TrackingSVNException("Project " + property.getSourceProject()
                        + " is not an SVN project");
            }
            trackedBuild = (AbstractBuild<?, ?>) tsvnAction.getTrackedBuild();
            tagAction = trackedBuild.getAction(SubversionTagAction.class);
        }

        ArrayList<SvnInfo> revisions = new ArrayList<SvnInfo>();
        for (SvnInfo info : tagAction.getTags().keySet()) {
            if (!property.isURLIgnored(info.url)) {
                revisions.add(info);
            }
        }
        RevisionParameterAction action = new RevisionParameterAction(revisions);
        build.addAction(action);

        build.addAction(new TrackingSVNAction(trackedBuild));

        AbstractBuild<?,?> t = trackedBuild;
        while (t != null) {
            fingerPrint(t, build, listener);
            TrackingSVNAction a = t.getAction(TrackingSVNAction.class);
            t = a != null ? (AbstractBuild<?, ?>) a.getTrackedBuild() : null;
        }
    }

    private void fingerPrint(AbstractBuild<?, ?> trackedBuild, AbstractBuild build, TaskListener listener) {
        try {
            String fileName = "Tracked Build";
            String digest = null;
            Fingerprinter.FingerprintAction tbfa = trackedBuild.getAction(Fingerprinter.FingerprintAction.class);
            if (tbfa != null) {
                Fingerprint fingerPrint = tbfa.getFingerprints().get(fileName);
                if (fingerPrint != null) {
                    digest = fingerPrint.getHashString();
                }
            }

            if (digest == null) {
                digest = Util.toHexString(MessageDigest.getInstance("MD5").digest(trackedBuild.getExternalizableId().getBytes("UTF-8")));
            }
            FingerprintMap map = Jenkins.getInstance().getFingerprintMap();
            Fingerprint f = map.getOrCreate(trackedBuild, fileName, digest);
            f.add(trackedBuild);
            f.add(build);
            Map<String, String> fingerprints = new HashMap<String, String>();
            fingerprints.put(fileName, digest);

            Fingerprinter.FingerprintAction fa = build.getAction(Fingerprinter.FingerprintAction.class);
            if (fa != null) {
                fa.add(fingerprints);
            } else {
                build.getActions().add(new Fingerprinter.FingerprintAction(build, fingerprints));
            }

            if (tbfa != null) {
                tbfa.add(fingerprints);
            } else {
                trackedBuild.getActions().add(new Fingerprinter.FingerprintAction(build, fingerprints));
            }

            trackedBuild.save();
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace(listener.error("error creating fingerprint"));
        } catch (IOException e) {
            e.printStackTrace(listener.error("error creating fingerprint"));
        }
    }

    @Override
    public void onCompleted(AbstractBuild build, TaskListener listener) {

        TrackingSVNRevisionParameterAction action = build.getAction(TrackingSVNRevisionParameterAction.class);
        if (action != null) {
            TriggerBuildAction tba = action.getBuild().getAction(TriggerBuildAction.class);
            if (tba == null) {
                logger.warning("Could not find TriggerBuildAction on " + build);
                return;
            }

            tba.addTriggeredBuild(build);

            listener.getLogger().println("Recorded completion on triggering build: " + tba.getBuild());
        }

    }
}
