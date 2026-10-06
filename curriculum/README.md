# Immutable Stage 1 snapshots

These byte-identical copies of the approved `Android data` Stage 1 snapshots
make this Android repository independently buildable. Original README/review/
conversion reports retain their historical provenance. No Stage 2 or staging
content is included. `SHA256SUMS` inventories all 39 copied files.

Debug assets use only `database-stage-1-formatted` catalog, manifest and subject
JSON. The legacy and reviewed snapshots are immutable JVM regression fixtures.
Release assets remain excluded. Do not normalize JSON line endings: manifest
hashes authenticate the original bytes. The three inventoried provenance
Markdown files also have narrowly scoped `-text` attributes so all 39 inventory
hashes remain stable across Git line-ending settings.
