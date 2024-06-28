# Release checklist

- [ ] Check that tests have passed on CI.
- [ ] Run manual test cases that are difficult to automate:
  - [ ] Fling animations can be interrupted by pressing anywhere.
  - [ ] Double-click-to-zoom animations can be interrupted **only** by starting another swipe gesture.
  - [ ] Images that use a placeholder play a cross-fade animation when the image is loaded.
- [ ] Change version name from SNAPSHOT to an actual name.
- [ ] Update project website with:
    - [ ] new version’s maven address
    - [ ] new APIs
- [ ] Commit `Prepare to release vX.X.X`. Do not push yet.
- [ ] Upload archives to maven.
  `g clean publish --no-parallel --no-daemon`
- [ ] Wait for artifacts to be available.
  `dependency-watch await me.saket.telephoto:zoomable:{version}`
- [ ] Ensure that the release is available on maven by using it in `:sample`
- [ ] Check that the library sources were correctly available.
- [ ] Run the sample app and perform sanity tests.
- [ ] Push commit.
- [ ] Generate a sample APK.
- [ ] Draft a changelog.
- [ ] Make a release on Github.
- [ ] Push a new commit `Prepare next development version` by bumping version and changing library version to SNAPSHOT.
