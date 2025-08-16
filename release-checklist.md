Checklist for creating a new release
====================================

## Pre-release Preparation
- [ ] Run all tests: `sbt test`
- [ ] Check that Node.js integration tests pass without errors
- [ ] Verify all documentation examples work with current version
- [ ] Review open issues/PRs that should be included in release
- [ ] Check all CI workflows are passing

## Version Updates
- [ ] Update version in `build.sbt`
- [ ] Update version in README installation instructions
- [ ] Update version in docs
- [ ] Update version badges in README
- [ ] Check example code snippets in README use current version

## Documentation Updates
- [ ] Update CHANGELOG.md:
    - [ ] Move "Unreleased" changes to new version section
    - [ ] Add release date
    - [ ] Add GitHub release link
    - [ ] Verify all significant changes are documented
    - [ ] Check formatting of changelog entries
- [ ] Update docs/_config.yml if needed
- [ ] Create new release documentation in docs/releases/
- [ ] Review/update API documentation if changed
- [ ] Check for any version references in tutorial/guide docs

## Release Verification
- [ ] Clean build: `sbt clean`
- [ ] Run full test suite again: `sbt test`
- [ ] Verify documentation site builds correctly
- [ ] Test published artifacts locally:
    - [ ] `sbt publishLocal`
    - [ ] Create test project using local version
    - [ ] Verify test project works with new version

## Publishing
- [ ] Push all changes to GitHub
- [ ] Wait for CI checks to pass
- [ ] Publish to Sonatype:
    - [ ] `sbt publishSigned`
    - [ ] Login to Sonatype and verify artifacts
    - [ ] `sbt sonatypeRelease`
    - [ ] Verify artifacts appear on Maven Central
- [ ] Create GitHub release with changelog content

## Post-release
- [ ] Verify installation instructions work with published version
- [ ] Update demo/example projects to use new version
- [ ] Merge `dev` branch into release branch
- [ ] Merge release branch into `stable` branch
- [ ] Create new "Unreleased" section in CHANGELOG.md
  ```
  ## [Unreleased]
  
  ### Added
  
  ### Fixed
  
  ### Changed
  
  ### Removed
  
  ### Deprecated
  ```
- [ ] Push post-release changes
