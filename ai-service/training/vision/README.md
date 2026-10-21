# Vision-training experiment

Train a ResNet18 classifier on labeled part images. This first experiment trains
the final classification layer of an ImageNet-pretrained backbone. The current
FastAPI CLIP feature remains the comparison baseline until the new model has
been evaluated on representative E-biessa photos.

## Intended feature scope

The user's requirement is recognition across the car-parts marketplace:
mechanical components, body and exterior parts, mirrors, lighting, and interior
parts. Under-the-hood alternators were a difficult diagnostic example, not a
restriction of the training objective. Keep this requirement when selecting new
datasets, expanding labels, training subsequent models, and evaluating results.

| Part group | Already represented in the first 50-class model | Important gaps to collect and train |
| --- | --- | --- |
| Body and exterior | Side mirror, spoiler | Door, bumper, hood, fender, trunk lid |
| Lighting | Headlights, taillights | Additional lighting assemblies if required by the catalogue |
| Interior | Instrument cluster, radio, shift knob | Seat, steering wheel, dashboard, door trim, centre console |
| Mechanical systems | Engine, brake, cooling, electrical, suspension and transmission components | Catalytic converter, complete clutch kit, turbocharger and other missing catalogue labels |

Presence in the label list means the model can return that label; accuracy for
each group still needs evaluation. The table does not claim complete coverage.
Align the final label inventory with the application's marketplace categories.
Record exact component types separately from complete assemblies, such as clutch
disc versus clutch kit and door panel versus door trim.

Collect both isolated parts and parts fitted to vehicles for every group, with
different angles, backgrounds, lighting and occlusion. For broader scene images
containing several objects, evaluate a detector or target-part selection/cropping
across exterior and interior scenes as well as engine bays. Report results per
part class and group; a strong alternator score cannot establish overall coverage.

## Dataset

Source: https://www.kaggle.com/datasets/gpiosenka/car-parts-40-classes

Kaggle metadata identifies the dataset as **50 Types of Car Parts -Image
Classification**, author `gpiosenka`, license **Apache 2.0**, about 315 MB.
Check the downloaded archive and labels before training. Keep the source and
its attribution with the experiment. The expected extracted layout is:

```
data/raw/train/<class>/<image>.jpg
data/raw/valid/<class>/<image>.jpg
data/raw/test/<class>/<image>.jpg
```

`data/` and `runs/` are local ignored files. Images and model weights are not
committed to Git. Preparation decodes images, checks labels, removes exact pixel
duplicates with held-out examples taking priority, and records the removals.
It cannot detect every near duplicate or identify images of the same car.

## Run from the repository root in PowerShell

The existing `.venv-training` has CUDA-enabled torch and torchvision. If using
another computer, create a separate environment and install compatible versions
using https://pytorch.org/get-started/locally/ before installing requirements.

```powershell
New-Item -ItemType Directory -Force .\ai-service\training\vision\data | Out-Null
curl.exe --fail --location --output .\ai-service\training\vision\data\car-parts-50.zip https://www.kaggle.com/api/v1/datasets/download/gpiosenka/car-parts-40-classes
& .\ai-service\.venv-training\Scripts\python.exe .\ai-service\training\vision\import_kaggle.py
& .\ai-service\.venv-training\Scripts\python.exe .\ai-service\training\vision\prepare_data.py --data .\ai-service\training\vision\data\raw
& .\ai-service\.venv-training\Scripts\python.exe .\ai-service\training\vision\train.py --epochs 5 --batch-size 32
```

Each run creates a new directory under `runs/` with `best.pt`, `metadata.json`,
`metrics.csv`, `training.log`, `test-results.json`, and `monitor.html`. Select the checkpoint by
validation loss; the test set is evaluated only afterward. Five epochs are a
pilot experiment, not a claim of finished training or production readiness.

## Screenshots of actual training

Keep the training terminal visible and use **Win + Shift + S** while it prints
epoch, batch, loss, and accuracy measurements. For graphs, serve the run directory:

```powershell
& .\ai-service\.venv-training\Scripts\python.exe -m http.server 8012 --bind 127.0.0.1 --directory .\ai-service\training\vision\runs
```

Open `http://127.0.0.1:8012/`, choose the run, and open `monitor.html`.
It refreshes every ten seconds using actual completed-epoch measurements.
Capture the running graph, the completed results, and the confusion matrix.
The checkpoint, manifest hash, metrics, and metadata preserve the evidence.

## Try your own photo after training

Use the actual run folder in place of `pilot-20261014`:

```powershell
& .\ai-service\.venv-training\Scripts\python.exe .\ai-service\training\vision\predict.py --checkpoint .\ai-service\training\vision\runs\pilot-20261014\best.pt --image "C:\path\to\alternator.jpg"
```

Compare the full photograph with a crop around the target part where useful. The
five returned scores are relative softmax scores, not calibrated probabilities.
This command runs locally and does not change the FastAPI configuration.

## Representative parts and the next acceptance check

A classifier labels the image as a whole. Evaluate isolated and installed parts
across mechanical, body/exterior, lighting and interior groups, including
occlusion, shadows, and different vehicles. Keep every photo of one physical
part/vehicle in the same split. A small public test split cannot establish
performance for all these settings. For wider photographs with several visible
components, train a detector with bounding-box annotations, or first let the user
crop the target part. Measure CLIP and the trained model on the same held-out
photos before selecting either model for the application.

## Recorded first pilot - 14 October 2026

- Dataset: 8,739 training images, 250 validation images, 250 test images, 50 classes.
- Audit: all images decoded; zero exact pixel duplicates found.
- Hardware: NVIDIA RTX 4060, CUDA-enabled PyTorch 2.11.0.
- Training: five epochs; frozen ResNet18 backbone, trained classification head;
  about 112 seconds of epoch processing (excluding download and startup).
- Checkpoint selected by lowest validation loss: epoch 5.
- Validation accuracy: 92.0%; held-out test top-1: 92.8%, top-3: 98.4%.
- Alternator and water pump: each 4/5 correct in the test set. Five examples per
  class are too few to establish dependable accuracy on mounted parts.
- Checkpoint reload and local prediction verified on a held-out alternator.
- Dataset preparation tests: duplicate leakage, conflicting labels, corrupt
  images - all passed.

The experiment is saved under `runs/pilot-20261014`. Screenshots and copies of
the measurements are under the repository's `output/vision-training/` folder.
The application's current vision endpoint has not been switched to this model.

## External photo review - 14 October 2026

`evaluate_photos.py` compares the app's cached CLIP model and exact prompt list
against the saved classifier, without updating either model. Its cases file
contains original paths, visual interpretations and optional normalized crop
coordinates. Example:

```powershell
& .\ai-service\.venv-training\Scripts\python.exe .\ai-service\training\vision\evaluate_photos.py --cases .\output\vision-training\user-cases.json --checkpoint .\ai-service\training\vision\runs\pilot-20261014\best.pt --output .\ai-service\training\vision\runs\photo-review-new
```

The first diagnostic review used 11 user-supplied photos, excluded from training.
No exact pixel matches were found in any original dataset split. The classifier
identified all three mounted alternators correctly; CLIP ranked clutch kit,
alternator and water pump first, respectively. Cropping corrected CLIP on the
third alternator. The classifier also recognized the isolated clutch plate that
CLIP called a brake rotor.

The classifier's missing classes matter: it mislabeled the two catalytic
converter examples and identified a pressure plate rather than the complete
clutch kit. Both models failed on the converter cutaway illustration. The
mounted exhaust image needs confirmation of its exact component type. There is
no overall accuracy claim for this small, mixed and partly ambiguous diagnostic
sample. Adding the missing categories and obtaining a separate set of new
mounted-part photos are prerequisites for choosing an application replacement.

Results and the illustrated report are in `runs/photo-review-20261014/` and
`output/vision-training/photo-review-20261014/`. Scores across the 22-class CLIP
taxonomy and the 50-class classifier are not directly comparable or calibrated.

## Expanded service and abstention - 14 October 2026

The runtime vocabulary now contains 74 labels across mechanical, exterior,
lighting and interior parts. It combines context-aware CLIP text embeddings and
an optional copy of the trained 50-class classifier. These extra labels are
zero-shot coverage, **not additional fine-tuned classifier classes**. More
reviewed training data is still needed before training those categories.

The service preserves image aspect ratio and the full frame for CLIP. It returns
one suggestion or an explicit `UNCERTAIN` response with no candidate. It rejects
blank/tiny images and uses non-part/scene prompts, score and margin thresholds,
absolute similarity and raw similarity-gap checks. Raw gaps help avoid CLIP's
logit scale making very similar labels look more decisive than they are. Strong
trained-class evidence may support diffuse mechanical/assembly predictions when
CLIP corroborates that component. Labels absent from the trained model require
stronger CLIP evidence. All thresholds remain heuristics, not calibration.

The final diagnostic review has 26 cases: the original 11 uploaded photos, 11
new Commons photos, three original dataset holdouts, and one synthetic blank
control. Commons category names were visually reviewed rather than treated as
ground truth. Their source pages, authors and licenses are recorded locally in
`output/vision-training/additional-photos/sources.json`. Review iterations were
used to improve preprocessing and abstention; this is a development validation
set, not an independent accuracy benchmark. These photos were not used for
classifier training.

Final behavior includes correct suggestions for the three mounted alternators,
clutch disc, radiator, headlight, starter, seat, mirror, radio and instrument
cluster. Unclear catalytic-converter and complete-kit examples, door trim,
mixed cabin scenes, a motorcycle scene, artwork, a cup and a blank control
receive `UNCERTAIN`. This conservatism trades recall for fewer false assertions;
category coverage does not imply every photo in that category will be recognized.

The final report is in `runs/expanded-review-release-20261014/` and copied to
`output/vision-training/expanded-review-release-20261014/`. Targeted verification
passed: 14 Python vision tests, 6 Spring client tests, 22 Angular component tests,
and 2 Playwright photo journeys. The existing 92.8% pilot test result applies
only to the trained 50-class classifier and its original test split, not to the
expanded service or these diagnostic photos.
