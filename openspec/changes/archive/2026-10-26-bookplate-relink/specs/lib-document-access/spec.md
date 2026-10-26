## MODIFIED Requirements

### Requirement: Test double
`lib/documentaccess/test` SHALL provide a fake document access backed by an in-memory tree of files and folders. A test
SHALL be able to add files and folders, queue the result of the next pick, grant a reference directly, delete or
rename items, and revoke a grant, and SHALL see the same listing, reading and reachability behaviour as on a device. On
the JVM it SHALL be able to load a directory from disk into the tree. A Koin module SHALL bind it.

By default a rename behaves as on Android: references to the old path are missing. When built to follow renames,
renaming or moving a granted, picked item SHALL move its grant with it, so references made from it keep working, as
iOS bookmarks do; renaming something inside a picked folder still makes references to the old path missing. The
fake SHALL count the reachability checks made, track the most that ran at once, and SHALL be able to make each
check take a given time.

#### Scenario: Picking in a test
- **WHEN** a test queues the folder `Books/Dune` and launches a folder pick
- **THEN** the pick returns a folder reference named `Dune` whose listing holds the files under `Books/Dune`

#### Scenario: Revoking in a test
- **WHEN** a test revokes the grant on `Books/Dune`
- **THEN** references under it are reported access-lost

#### Scenario: Rename on Android
- **WHEN** the granted folder `Books/Dune` is renamed to `Books/Dune 2` by a default fake
- **THEN** references made from `Books/Dune` are missing

#### Scenario: Rename followed like iOS
- **WHEN** the same rename is made by a fake that follows renames
- **THEN** references made from `Books/Dune`, and the files listed through them, are reachable and read from
  `Books/Dune 2`

#### Scenario: Rename inside a followed folder
- **WHEN** a file inside the granted `Books/Dune` is renamed by a fake that follows renames
- **THEN** the reference to the file's old path is missing

#### Scenario: Concurrent checks
- **WHEN** checks take 100 ms each and three are started together
- **THEN** the fake reports three checks with a peak of three at once
