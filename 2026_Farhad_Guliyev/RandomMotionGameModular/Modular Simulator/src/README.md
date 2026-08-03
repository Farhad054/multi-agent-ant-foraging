# Ant Foraging Simulator — Multi-Agent Strategies

Java Swing demo of the classic ant nest↔food foraging experiment, with four
multi-agent movement strategies implemented as plug-in `MovementStrategy` classes
(Strategy pattern). The environment adds a nest, a food source, two independent
pheromone trail grids, and static/dynamic obstacles.

## Strategies

| Strategy | Coordination mechanism | What to look for |
|----------|------------------------|------------------|
| **Stochastic** | Roulette-wheel over pheromone + goal + inertia cues | Chaotic baseline; ants wander but still forage. |
| **Stigmergy** | Indirect, via the shared pheromone field (trail-dominant) | A single sharp trail self-reinforces between nest and food. |
| **Game Theory** | Congestion game — best response to neighbour crowding | Straight, coordinated paths; one main green + one main blue lane. |
| **Reinforcement Learning** | Colony-shared tabular Q-learning (ε-greedy) | Chaotic at first, then commits to a learned route (~45 s). |

### Trail semantics (shared by all strategies)
- Searching ants deposit the **nest trail (blue)** and follow the **food trail (green)**.
- Returning ants deposit the **food trail (green)** and follow the **nest trail (blue)**.

## Build & Run
From this `src` directory:
```bash
javac *.java
java RandomMotionGameModular
```

## Tests
A head-less behavioural regression test lives in `../test`. It drives the real
game loop and checks that every strategy forages without freezing, that the two
trail channels stay independent, and that the RL agent measurably learns.
From the parent `Modular Simulator` directory:
```bash
javac -d out src/*.java test/*.java
java -ea -Djava.awt.headless=true -cp out StrategyBehaviorTest
```
Exits non-zero if any check fails.
