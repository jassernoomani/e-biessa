"""Conservative abstention rules; thresholds are heuristics, not calibration."""

from .vision_taxonomy import CLASSIFIER_LABELS, PART_TAXONOMY


def choose_part(scores, similarities, classifier=None, minimum_score=0.35, minimum_margin=0.12,
                minimum_similarity=0.20, minimum_similarity_margin=0.01, minimum_untrained_score=0.85):
    if len(scores) < 2 or len(scores) != len(similarities):
        return None, "The photo could not be assessed. Try a clear close-up of one part."
    order = sorted(range(len(scores)), key=lambda index: scores[index], reverse=True)
    first, second = order[:2]
    if first >= len(PART_TAXONOMY):
        return None, "The photo does not clearly show one identifiable car part. Crop the target part or take a closer photo."
    if similarities[first] < minimum_similarity:
        return None, "The photo is outside the supported examples or too unclear. Try another angle or type the part name."
    clip_label = PART_TAXONOMY[first][0]
    if classifier and len(classifier) >= 2:
        label = CLASSIFIER_LABELS.get(classifier[0][0])
        strong = classifier[0][1] >= 0.60 and classifier[0][1] - classifier[1][1] >= 0.25
        if strong and label:
            index = next((i for i, item in enumerate(PART_TAXONOMY) if item[0] == label), None)
            corroborated = index in order[:3]
            if index is not None and scores[first] < minimum_score:
                corroborated = corroborated or (similarities[index] >= max(0.25, minimum_similarity)
                    and similarities[first] - similarities[index] <= 0.01)
            covered_context = clip_label in CLASSIFIER_LABELS.values() or (
                clip_label in {"clutch kit", "complete engine assembly"} and scores[first] < minimum_score)
            if covered_context and corroborated and similarities[index] >= minimum_similarity:
                return (index, float(classifier[0][1])), ""
            # A label the classifier was never trained on cannot be rejected on
            # the basis of its forced prediction among the older 50 classes.
            if clip_label in CLASSIFIER_LABELS.values():
                return None, "The models disagree about the part. Crop it, use a clearer angle, or search by name."
    required_score = minimum_score if clip_label in CLASSIFIER_LABELS.values() else max(minimum_score, minimum_untrained_score)
    if (scores[first] < required_score or scores[first] - scores[second] < minimum_margin
            or similarities[first] - similarities[second] < minimum_similarity_margin):
        return None, "Several part types look similar in this photo. Try a closer photo or search by name."
    return (first, float(scores[first])), ""
