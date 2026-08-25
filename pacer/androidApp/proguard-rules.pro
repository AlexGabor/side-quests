# App-specific R8 rules for Pacer.
#
# Empty on purpose. Nothing here reaches for a class by name — no reflection, no
# serialization, no XML-inflated views — so R8 can see the whole graph from the entry
# points in the manifest, and every library that does need rules ships its own.
#
# The file still exists because the build type points at it: keep rules belong next to
# the code that needs them, so this is where they go when something eventually does.
