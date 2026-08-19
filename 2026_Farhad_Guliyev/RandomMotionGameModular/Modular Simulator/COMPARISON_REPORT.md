# Multi-Agent Ant Foraging — Comparison of Movement Strategies

**Project:** Multi-Agent Ant Foraging Simulation (Prometheus Lab)
**Scope:** Behavioural and performance comparison of four multi-agent foraging
algorithms — **Stochastic**, **Stigmergy**, **Game Theory**, and
**Reinforcement Learning** — implemented as plug-in `MovementStrategy` classes in
the shared Java Swing simulator.
**Acknowledgements:** Testing and validation support was provided by
**Chenyu Zhang**.

---

## 1. Executive summary

All four algorithms solve the nest↔food foraging task, but they differ sharply in
efficiency and coordination, and each exhibits the behaviour its algorithm class
predicts.

Crucially, **all four now operate under the same information model**: no strategy
is told where the food is in advance. Each ant can only sense the goal within a
limited radius and must otherwise *discover* food by exploring and by following
the pheromone trails laid by ants that found it first. (This corrects an earlier
version in which the Game Theory ants were handed the exact food coordinates from
the start — see §3.1.) On a level playing field the results are:

| Rank | Strategy | Sustained throughput* | vs. best | Character |
|------|----------|----------------------:|---------:|-----------|
| 1 | Reinforcement Learning | **~49** trips / window | 100% | Learns, then commits to sharp routes |
| 2 | Game Theory | ~43 trips / window | ~88% | Coordinated, separated lanes; very stable |
| 3 | Stochastic | 12.5 trips / window | 26% | Chaotic baseline |
| 4 | Stigmergy | 10.8 trips / window | 22% | Single congested trail |

\* Mean completed round-trips per 2000-tick window (≈32 s) in the converged phase,
averaged over multiple trials. RL and Game Theory are the two top performers and
now perform **comparably**: RL converges to a slight lead but with higher
run-to-run variance, while Game Theory is remarkably steady from the first window.

**Headline finding:** the two *coordinated / adaptive* strategies deliver roughly
**3.5–4× the useful throughput** of the uncoordinated baselines — while, notably,
**not moving any faster per tick**. Most of the baselines' motion is wasted
wandering; the smart strategies convert nearly all of their motion into progress.

---

## 2. The four algorithms

Each ant chooses one of 8 compass moves per tick. The environment provides a nest,
a food source, and **two independent pheromone grids** that evaporate over time:

- **Searching** ants (no food) deposit the **blue nest trail**; they follow the
  **green food trail** to find food.
- **Returning** ants (carrying food) deposit the **green food trail**; they follow
  the **blue nest trail** to get home.

| Strategy | Coordination mechanism | Decision rule |
|----------|------------------------|---------------|
| **Stochastic** | Probabilistic, pheromone-recruited, goal sensed only when near | Roulette-wheel over weighted directions |
| **Stigmergy** | Indirect, through the shared pheromone field (trail-dominant) | Roulette-wheel dominated by trail concentration |
| **Game Theory** | Congestion game — best response to neighbour crowding | Argmax of payoff = benefit − congestion |
| **Reinforcement Learning** | Colony-shared tabular Q-learning | ε-greedy over a learned Q-table |

---

## 3. Methodology

- **Simulator:** the exact production strategy classes, driven head-less through
  the real `GamePanel` game loop (identical evaporation, per-pixel collision, and
  obstacle handling to the interactive app).
- **Environment:** 900×600 field, nest at (80, 80), food at (800, 500), two static
  obstacles, **12 ants**, 5 px/tick, pheromone evaporation 0.5%/tick.
- **Run length:** 12 000 ticks per trial (≈3.2 min at 60 fps), six 2000-tick
  windows (≈32 s each). Metrics averaged over **6 trials per strategy**; the RNG is
  clock-seeded, so figures are averages and vary slightly run-to-run.
- **Metrics:** completed round-trips (find food → return to nest); sustained
  throughput (converged windows); average per-tick displacement (px), which
  separates raw motion from useful progress.

### 3.1 Fairness — equal information for every strategy

Following review feedback, the Game Theory strategy was revised so that it uses
**the same information model as the Stochastic baseline**: the goal exerts a strong
pull only within a 450-px sensing radius, and beyond that the ant must find food by
exploring outward and by following the recruiting pheromone trail. Previously its
payoff used the exact goal distance at any range — effectively knowing the food
location from tick zero. The distinctive game-theoretic ingredient (best-response
congestion avoidance) is unchanged; only its *knowledge* was brought in line with
the others so the comparison is fair. As expected, its throughput fell from ~58 to
~43 trips/window once it had to discover food like everyone else.

---

## 4. Results

### 4.1 Aggregate metrics (mean of 6 trials, 12 000 ticks)

| Metric | Stochastic | Stigmergy | Game Theory | RL |
|--------|-----------:|----------:|------------:|---:|
| **Total round-trips** | 68.8 | 64.5 | 257.2 | **283.7** |
| **Sustained throughput** (trips/window) | 12.5 | 10.8 | 43.2 | **~49** |
| **avg movement** (px/tick) | 5.82 | 5.16 | 5.18 | 5.06 |
| **First food found** (ticks) | 42 | 77 | 31 | 102 |

### 4.2 Throughput over time (trips per 2000-tick window)

| Window (≈32 s each) | W1 | W2 | W3 | W4 | W5 | W6 |
|---------------------|---:|---:|---:|---:|---:|---:|
| Stochastic | 8.8 | 11.2 | 13.0 | 12.5 | 11.8 | 11.5 |
| Stigmergy | 11.0 | 10.7 | 10.8 | 10.5 | 10.2 | 11.3 |
| Game Theory | 43.2 | 42.7 | 43.7 | 42.5 | 42.3 | 42.8 |
| **RL** | **21.5** | **48.7** | **54.8** | **51.5** | **53.7** | **53.5** |

Stochastic, Stigmergy and Game Theory are **stable from the first window** — their
policies don't change over time. **RL is the only strategy that improves**:
throughput more than doubles from W1→W2 as ε decays and the Q-table sharpens, then
plateaus at or above the Game-Theory level. That ramp is the signature of learning.

### 4.3 Context: theoretical ceiling

The shortest nest↔food round trip is ≈1670 px; at ~5.5 px/tick of net progress
that is ~300 ticks/trip, a ceiling of roughly **75–80 round-trips per window** for
12 ants on perfectly straight, uncongested paths. Against that ceiling: converged
RL reaches ~65%, Game Theory ~55%, and the two baselines ~15% — quantifying how
much throughput coordinated or learned routing recovers over chaotic or congested
foraging.

---

## 5. Per-strategy analysis

### 5.1 Stochastic — chaotic baseline
Weighted-random motion. Trails sprawl diffusely and no clean path stabilises. The
ants move the **fastest** per tick of any strategy yet complete the **fewest** trips
— motion without much progress. This is the intended reference point against which
the coordinated strategies are measured.

### 5.2 Stigmergy — single self-reinforcing trail
A single sharp nest↔food corridor emerges as ants deposit onto, and then prefer,
the same cells (positive feedback). Its characteristic weakness is visible too:
with no congestion avoidance the ants **bunch up** on that one trail, capping
throughput near the Stochastic level despite the cleaner path.

### 5.3 Game Theory — coordinated separated lanes
Best-response-to-congestion pushes ants off crowded cells, so the colony
self-organises into **separated outbound and return lanes** around the obstacles —
a one-way traffic circuit. Individual paths are longer, but aggregate throughput is
high because ants flow in parallel without jamming. Now that it must discover food
fairly, its throughput is ~43 trips/window — second-best, and the **most stable** of
all four (essentially flat across every window).

### 5.4 Reinforcement Learning — learns, then commits
Early on the ants explore (high ε) and throughput is low; as ε decays the shared
Q-table converges and the colony commits to **sharp, thin routes**. Because the
carrying-flag is part of the state, the searching and returning policies settle on
**different learned routes** (distinct blue and green paths). Converged throughput
is the highest of the four. The doubling of throughput in the first minute is direct
evidence the agent is learning rather than following a fixed rule.

---

## 6. Comparative discussion

- **Throughput:** RL ≈ Game Theory ≫ Stochastic ≈ Stigmergy. With equal information,
  the adaptive and coordinated strategies are close at the top, and both deliver
  ~3.5–4× the useful output of the uncoordinated baselines.
- **Adaptivity vs. stability:** RL is the only strategy that *improves* during a run
  and reaches the highest converged throughput, but it is more variable. Game Theory
  never learns yet is the most *stable* performer from the very first window. This
  is the central trade-off the two top strategies illustrate.
- **Efficiency of motion:** the strong strategies achieve far more useful work at
  roughly the *same or lower* per-tick movement — "moving a lot" ≠ "foraging well."
- **Coordination style:** Stigmergy coordinates through the *environment* (shared
  trail); Game Theory through *mutual observation* of other agents; RL through a
  *shared learned value function*; Stochastic barely coordinates at all. The four
  span a useful taxonomy of multi-agent coordination.

---

## 7. Limitations & future work

1. **Static single-source environment** — one nest, one food source, fixed
   obstacles. A dynamic environment (relocating food, moving obstacles) would
   further separate the adaptive RL strategy from the fixed-policy strategies.
2. **Game Theory and RL do not read the pheromone trails for their core decision**
   (Game Theory now uses trails only for recruitment when the goal is out of sensing
   range; RL uses its Q-table). Letting both fully consume trail information would
   allow a study of *hybrid* coordination.
3. **RL is tabular**, appropriate for this discrete state space; a larger or
   continuous environment would need function approximation.
4. **Non-deterministic runs** — all quantitative claims are averages over trials.

---

## 8. Conclusions

- All four algorithms are **implemented correctly** and, under an **equal
  information model**, demonstrate a clean spectrum from uncoordinated to
  coordinated to adaptive multi-agent foraging.
- **Coordination and learning pay off:** RL and Game Theory deliver ~3.5–4× the
  throughput of the baselines while expending no more motion per tick.
- **Learning edges out fixed coordination:** once every strategy must discover food
  fairly, the learning agent (RL) converges to the highest throughput, with the
  game-theoretic strategy a close and notably stable second.

---

*Data generated head-less from the production strategy code via the real game loop;
6 trials × 12 000 ticks per strategy. Reproduce with the metrics harness / regression
test (`test/StrategyBehaviorTest.java`). Figures vary slightly run-to-run due to
non-deterministic seeding. Testing support: Chenyu Zhang.*
