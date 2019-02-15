package org.jazzcommunity.GitConnectorService.dcc.service;

import com.ibm.team.repository.service.TeamRawService;
import com.siemens.bt.jazz.services.base.rest.parameters.PathParameters;
import com.siemens.bt.jazz.services.base.rest.parameters.RestRequest;
import com.siemens.bt.jazz.services.base.rest.service.AbstractRestService;
import java.util.ArrayList;
import java.util.Date;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.Marshaller;
import org.apache.commons.lang.RandomStringUtils;
import org.apache.commons.logging.Log;
import org.apache.http.entity.ContentType;
import org.jazzcommunity.GitConnectorService.dcc.data.Commit;
import org.jazzcommunity.GitConnectorService.dcc.data.LinkCollector;
import org.jazzcommunity.GitConnectorService.dcc.data.TimeOutArrayList;
import org.jazzcommunity.GitConnectorService.dcc.data.WorkItemLinkFactory;
import org.jazzcommunity.GitConnectorService.dcc.net.PaginatedRequest;
import org.jazzcommunity.GitConnectorService.dcc.xml.Commits;

public class CommitService extends AbstractRestService {

  // TODO: This should be configurable
  private static final String DEFAULT_SIZE = "25";

  // TODO: Implement cache clearing
  private static final ConcurrentHashMap<String, TimeOutArrayList<Commit>> cache =
      new ConcurrentHashMap<>();

  public CommitService(
      Log log,
      HttpServletRequest request,
      HttpServletResponse response,
      RestRequest restRequest,
      TeamRawService parentService,
      PathParameters pathParameters) {
    super(log, request, response, restRequest, parentService, pathParameters);
  }

  /**
   * Example output generated by current git commit query Parameter fields:
   * commits/commit/(url|commiterName|comment|sha|date|commiterEmail|repositoryKey) Parameter
   * modifiedsince: 2018-11-23T09:06:54.793-0600 Parameter async: true
   */
  @Override
  public void execute() throws Exception {
    // TODO: Improve error handling
    String id = request.getParameter("id");
    String size =
        request.getParameter("size") != null ? request.getParameter("size") : DEFAULT_SIZE;

    // TODO: Remove this after testing default size
    if (size == null || Integer.valueOf(size) == 0) {
      // this should just return the entire payload... though I'm not sure yet if we actually want
      // to support that operation
      throw new NoSuchMethodException("Invalid method call");
    }

    if (id != null) {
      // this is the continuation of an existing request
      // for now, we will just expect that the method is called with the proper size arguments
      // TODO: Will have to take into account that the slice size might be larger than the data
      PaginatedRequest pagination =
          PaginatedRequest.fromRequest(parentService.getRequestRepositoryURL(), request, id);

      // this is an easy workaround for just now, definitely doesn't cover all cases
      TimeOutArrayList<Commit> commits = cache.get(id);
      int end = Math.min(pagination.getEnd(), commits.size());

      // this is pretty much the same as in the other method, we now just build the responses
      Commits answer = new Commits();
      answer.setHref(pagination.getNext().toString());

      // check if we are done
      // TODO: this also needs a vastly superior solution

      if (pagination.getEnd() >= commits.size() || commits.isEmpty()) {
        answer.setHref(null);
        answer.setRel(null);
        // because we're at the end, we can release the array list for GC
        cache.remove(id);
      }

      answer.addCommits(commits.subList(pagination.getStart(), end));
      response.setContentType(ContentType.APPLICATION_XML.toString());
      response.setCharacterEncoding("UTF-8");
      Marshaller context = JAXBContext.newInstance(Commits.class).createMarshaller();
      context.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, true);
      context.marshal(answer, response.getWriter());

      log.error(pagination);

      return;
    }

    if (id == null) {
      // before we start, check if any previous jobs need to be dumped
      Date now = new Date();
      for (Entry<String, TimeOutArrayList<Commit>> entry : cache.entrySet()) {
        if (entry.getValue().dump(now) && !entry.getKey().equals(id)) {
          cache.remove(entry.getKey());
        }
      }

      // check for archived attribute
      boolean includeArchived =
          request.getParameter("archived") != null
              ? Boolean.valueOf(request.getParameter("archived"))
              : false;

      // this is the start of a new dcc extraction job
      // first, we need to start a new 'collection' session.
      System.out.println(String.format("Inluding archived: %s", includeArchived));
      ArrayList<WorkItemLinkFactory> links =
          new LinkCollector(this.parentService).collect(includeArchived);
      // actually, this is not what should be cached yet. This is only just the query with work
      // items that have commit links. What I need now is another method that will resolve ALL of
      // these in a flat list, and only that should then be cached. This needs to be an extra step
      // and not right into the commit wrapper class.

      // we now need to flatten the collection of all git links while resolving them
      // this is what will then be cached
      TimeOutArrayList<Commit> commits = new TimeOutArrayList<>();
      for (WorkItemLinkFactory link : links) {
        commits.addAll(link.resolveCommits());
      }
      log.error(String.format("Initial collection found %s links", commits.size()));
      // this will be cached for all subsequent requests
      // the key is just a random string to index into the hashmap
      String random = RandomStringUtils.randomAlphanumeric(1 << 5);
      cache.put(random, commits);
      // now, we need to create a pagination object with the next payload
      PaginatedRequest pagination =
          PaginatedRequest.fromRequest(parentService.getRequestRepositoryURL(), request, random);

      // with this set, we can now create the first answer payload
      Commits answer = new Commits();
      answer.setHref(pagination.getNext().toString());
      // and then fill them with the paginated values
      if (pagination.getEnd() > Integer.valueOf(size)) {
        answer.addCommits(commits.subList(pagination.getStart(), Integer.valueOf(size)));
      } else {
        answer.addCommits(commits.subList(pagination.getStart(), pagination.getEnd()));
      }
      // and write it back as our response
      // TODO: extract xml creation functionality to separate class
      response.setContentType(ContentType.APPLICATION_XML.toString());
      // dcc doesn't send a required encoding, but will error out on anything that isn't utf-8. I'm
      // not sure this is correct for every deployment configuration, but has been the same with
      // every instance that I have tested so far.
      response.setCharacterEncoding("UTF-8");
      Marshaller context = JAXBContext.newInstance(Commits.class).createMarshaller();
      // makes the output human readable. This should probably be a flag or something so that we
      // don't create additional overhead when running in production.
      context.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, true);
      context.marshal(answer, response.getWriter());

      log.error(pagination);

      return;
    }

    throw new NoSuchMethodException("Invalid method call");
  }
}
