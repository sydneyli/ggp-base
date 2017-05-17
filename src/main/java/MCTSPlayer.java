import java.time.Duration;
import java.time.Instant;

import org.ggp.base.player.gamer.event.GamerSelectedMoveEvent;
import org.ggp.base.player.gamer.statemachine.sample.SampleGamer;
import org.ggp.base.util.statemachine.Move;
import org.ggp.base.util.statemachine.exceptions.GoalDefinitionException;
import org.ggp.base.util.statemachine.exceptions.MoveDefinitionException;
import org.ggp.base.util.statemachine.exceptions.TransitionDefinitionException;

public class MCTSPlayer extends SampleGamer {
	MonteCarloTreeSearch tree;

	@Override
	public void stateMachineMetaGame(long timeout) throws TransitionDefinitionException, MoveDefinitionException, GoalDefinitionException
	{
		super.stateMachineMetaGame(timeout);
		tree = new MonteCarloTreeSearch(getStateMachine(), getRole());
	}

	@Override
	public Move stateMachineSelectMove(long timeout)
			throws TransitionDefinitionException, MoveDefinitionException, GoalDefinitionException {
		Instant start = Instant.now();
		Instant max = Instant.ofEpochMilli(timeout);
		max = max.minus(Duration.ofSeconds(3));
		tree.updateRoot(getCurrentState());
        while(max.compareTo(Instant.now()) > 0) {
			tree.search();
		}

		Move selection = tree.chooseMove();

		Instant stop = Instant.now();
		notifyObservers(new GamerSelectedMoveEvent(getStateMachine().getLegalMoves(getCurrentState(), getRole()),
				selection, Duration.between(start, stop).toMillis()));
		return selection;
	}

}
