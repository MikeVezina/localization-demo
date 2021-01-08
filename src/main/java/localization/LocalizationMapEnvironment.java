package localization;

import epistemic.agent.EpistemicAgent;
import jason.asSyntax.ASSyntax;
import jason.asSyntax.Literal;
import jason.asSyntax.ObjectTermImpl;
import jason.asSyntax.Structure;
import jason.environment.Environment;
import jason.infra.centralised.RunCentralisedMAS;
import localization.models.LocalizationMapModel;
import localization.models.MapEvent;
import localization.perception.AgentPerspectiveMap;
import localization.perception.Perception;
import localization.view.LocalizationMapView;

import java.util.*;

public class LocalizationMapEnvironment extends Environment implements MapEventListener {


    // Hack to access from agent....
    public static LocalizationMapEnvironment instance;
    private final AgentPerspectiveMap observedMap;

    private final LocalizationMapView localizationMapView;
    private final LocalizationMapModel localizationMapModel;
    private final Queue<MapEvent> mapEventQueue;

    public LocalizationMapEnvironment() {
        instance = this;
        this.mapEventQueue = new LinkedList<>();
        localizationMapView = new LocalizationMapView();
        localizationMapModel = localizationMapView.getModel();
        this.observedMap = new AgentPerspectiveMap();

        // Generate the map information beliefs based on the loaded map
        localizationMapModel.generateASL();


        localizationMapModel.addMapListener(this);
        localizationMapView.setVisible(true);
    }

    @Override
    public void init(String[] args) {
        super.init(args);
    }

    @Override
    public synchronized Collection<Literal> getPercepts(String agName) {
        // No change in perceptions if the agent hasn't moved
        // Also, keep current percepts if the agent is not done reasoning
        super.clearPercepts(agName);

        var curPercepts = super.getPercepts(agName);

        if (curPercepts == null)
            curPercepts = new ArrayList<>();

        // add persistent percepts
        curPercepts.addAll(getPersistentPercepts());

        // Add always-present percepts
        if (mapEventQueue.isEmpty() && curPercepts.isEmpty())
            return curPercepts;

        // If no events need to be processed, return null (no change in percepts)
        if (mapEventQueue.isEmpty())
            return null;


        // Get next event to process
        MapEvent nextEvent = mapEventQueue.poll();

        EpistemicAgent agent = (EpistemicAgent) RunCentralisedMAS.getRunner().getAgs().get(agName).getTS().getAg();
        boolean hasChanged = observedMap.agentMoved(nextEvent.getMoveDirection(), new Perception(nextEvent.getRawPerceptions()));

        if(hasChanged) {
            for (var lit : observedMap.toMapBeliefData())
                agent.getBB().add(lit);
            agent.rebuildDistribution();
        }
        curPercepts.add(ASSyntax.createAtom("moved"));
//        curPercepts.add(ASSyntax.createLiteral(Literal.LPos, "location", ASSyntax.createNumber(2), ASSyntax.createNumber(1)));
//        curPercepts.add(ASSyntax.createLiteral(Literal.LPos, "location", ASSyntax.createNumber(1), ASSyntax.createNumber(1)));
//        curPercepts.add(ASSyntax.createLiteral(Literal.LNeg, "location", ASSyntax.createNumber(3), ASSyntax.createNumber(3)));
        curPercepts.addAll(nextEvent.getPerceptions());
        curPercepts.add(ASSyntax.createLiteral("lastMove", nextEvent.getMoveDirectionAtom()));

        curPercepts.add(ASSyntax.createLiteral("cards", ASSyntax.createString("Bob"), ASSyntax.createString("A8")));
        curPercepts.add(ASSyntax.createLiteral("cards", ASSyntax.createString("Charlie"), ASSyntax.createString("A8")));
        curPercepts.add(ASSyntax.createLiteral("peekedCard", ASSyntax.createString("A")));
        return curPercepts;
    }

    @Override
    public boolean executeAction(String agName, Structure act) {
        return true;
    }

    private List<Literal> getPersistentPercepts() {
        List<Literal> persistPercepts = new ArrayList<>();

        persistPercepts.add(ASSyntax.createLiteral("modelObject", new ObjectTermImpl(localizationMapModel)));



        // Add dynamic map knowledge
//        persistPercepts.addAll(getModel().dumpMapBeliefsToBB());

        if (localizationMapView.getSettingsPanel().shouldAutoMove())
            persistPercepts.add(ASSyntax.createLiteral("autoMove"));

        return persistPercepts;
    }


    @Override
    public synchronized void agentMoved(MapEvent event) {
        this.mapEventQueue.add(event);

        // Disable input until agent is ready.
        getModel().signalInput(false);

        // Inform that agents need new percepts (otherwise there is a delay!)
        if (this.getEnvironmentInfraTier() != null)
            this.getEnvironmentInfraTier().informAgsEnvironmentChanged();
    }

    public LocalizationMapModel getModel() {
        return localizationMapModel;
    }
}
