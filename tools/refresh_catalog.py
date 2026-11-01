"""Refresh film metadata for the next APK build; never downloads poster files."""
import argparse
import csv
import datetime
import gzip
import io
import json
from pathlib import Path
import urllib.request


def rows(dataset):
    request = urllib.request.Request(
        f"https://datasets.imdbws.com/{dataset}.tsv.gz",
        headers={"User-Agent": "CinemaFramePersonal/0.3 metadata-builder"},
    )
    with urllib.request.urlopen(request, timeout=120) as response:
        with gzip.GzipFile(fileobj=response) as compressed:
            yield from csv.DictReader(io.TextIOWrapper(compressed, encoding="utf-8"), delimiter="\t")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--min-rating", type=float, default=6)
    parser.add_argument("--min-votes", type=int, default=10000)
    args = parser.parse_args()
    ratings = {}
    for row in rows("title.ratings"):
        rating, votes = float(row["averageRating"]), int(row["numVotes"])
        if rating >= args.min_rating and votes >= args.min_votes:
            ratings[row["tconst"]] = (rating, votes)
    movies = []
    for row in rows("title.basics"):
        if row["tconst"] not in ratings or row["titleType"] != "movie" or row["isAdult"] != "0" or row["startYear"] == "\\N":
            continue
        rating, votes = ratings[row["tconst"]]
        movies.append({
            "id": row["tconst"], "title": row["primaryTitle"],
            "originalTitle": row["originalTitle"], "year": int(row["startYear"]),
            "rating": rating, "votes": votes,
            "genres": [] if row["genres"] == "\\N" else row["genres"].split(","),
        })
    if not movies:
        raise RuntimeError("No movies received; existing catalogue was not replaced")
    movies.sort(key=lambda movie: (-movie["votes"], movie["id"]))
    payload = json.dumps({
        "source": "https://datasets.imdbws.com/",
        "updated": datetime.datetime.now(datetime.timezone.utc).date().isoformat(),
        "movies": movies,
    }, ensure_ascii=False, separators=(",", ":"))
    project = Path(__file__).resolve().parents[1]
    for relative in ("data/movies.json", "android/assets/movies.json"):
        target = project / relative
        target.parent.mkdir(parents=True, exist_ok=True)
        temporary = target.with_suffix(".json.tmp")
        temporary.write_text(payload, encoding="utf-8")
        temporary.replace(target)
    print(json.dumps({"indexed": len(movies), "default_eligible": sum(movie["rating"] >= 7 and movie["votes"] >= 50000 for movie in movies)}))


if __name__ == "__main__":
    main()
