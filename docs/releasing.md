# Release checklist

- Check that sample compiles.
- Run tests.
- Change version name from SNAPSHOT to an actual name.
- Update README and project website with:
    - new version’s maven address
    - new APIs
- Commit `Prepare to release vX.X.X`. DO NOT PUSH YET.
- Upload archives to maven.
  `g clean publish --no-parallel --no-daemon`
- Ensure that the release is available on maven by using it `:sample`
- Run the sample app and ensure everything works. Telephoto has some test cases that aren't automated and must be manually tested:
  - When a fling animation is ongoing, tapping anywhere stops the animation.
- Push commit.
- Generate a sample APK.
- Draft a changelog.
- Make a release on Github.
- Push a new commit `Prepare next development version` by bumping version and changing library version to SNAPSHOT.
