# Release checklist

- Check that sample compiles.
- Run tests. Ensure that tests passed on CI.
- Change version name from SNAPSHOT to an actual name.
- Update project website with:
    - new version’s maven address
    - new APIs
- Commit `Prepare to release vX.X.X`. DO NOT PUSH YET.
- Upload archives to maven.
  `g clean publish --no-parallel --no-daemon`
- Wait for artifacts to be available.
  `dependency-watch await me.saket.telephoto:zoomable:…`
- Ensure that the release is available on maven by using it `:sample`
- Check that library sources are available.
- Run the sample app and ensure everything works. 
- Run manual test cases that are difficult to automate:
  - Fling animations can be interrupted by pressing anywhere.
  - Double-tap-to-zoom animations can be interrupted **only** by starting another swipe gesture. 
- Push commit.
- Deploy changes to project website:
  `mkdocs gh-deploy`
- Generate a sample APK.
- Draft a changelog.
- Make a release on Github.
- Push a new commit `Prepare next development version` by bumping version and changing library version to SNAPSHOT.
